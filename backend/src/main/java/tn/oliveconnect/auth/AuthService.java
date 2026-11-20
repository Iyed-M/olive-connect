package tn.oliveconnect.auth;

import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import tn.oliveconnect.common.BusinessRuleException;
import tn.oliveconnect.user.MeDto;
import tn.oliveconnect.user.User;
import tn.oliveconnect.user.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenStore refreshTokenStore;
    private final LoginRateLimiter loginRateLimiter;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
            RefreshTokenStore refreshTokenStore, LoginRateLimiter loginRateLimiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenStore = refreshTokenStore;
        this.loginRateLimiter = loginRateLimiter;
    }

    public Session login(String email, String password, String ip) {
        String normalizedEmail = email.trim().toLowerCase();
        loginRateLimiter.checkAllowed(normalizedEmail, ip);
        // every failure throws the same exception: the caller cannot tell which check failed
        User user = userRepository.findByEmail(normalizedEmail)
                .filter(User::isEnabled)
                .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                .orElse(null);
        if (user == null) {
            loginRateLimiter.recordFailure(normalizedEmail, ip);
            throw new InvalidCredentialsException();
        }
        loginRateLimiter.reset(normalizedEmail, ip);
        return newSession(user);
    }

    /** Rotates the refresh token. Empty means the token is unknown, already used or its User is unusable. */
    public Optional<Session> refresh(String refreshToken) {
        Optional<String> userId = refreshTokenStore.consume(refreshToken);
        if (userId.isEmpty()) {
            return Optional.empty();
        }
        // re-read the User: a role change applies now, a disabled or deleted User loses every session
        Optional<User> user = userRepository.findById(userId.get()).filter(User::isEnabled);
        if (user.isEmpty()) {
            refreshTokenStore.revokeAll(userId.get());
            return Optional.empty();
        }
        return Optional.of(newSession(user.get()));
    }

    public void logout(String refreshToken) {
        refreshTokenStore.revoke(refreshToken);
    }

    private Session newSession(User user) {
        AccessToken token = jwtService.accessToken(user);
        String refreshToken = refreshTokenStore.create(user.getId());
        return new Session(new LoginResponse(token.value(), token.expiresIn(), MeDto.from(user)), refreshToken);
    }

    public MeDto me(String userId) {
        return userRepository.findById(userId)
                .map(MeDto::from)
                .orElseThrow(() -> new UnauthenticatedException("Your session is no longer valid"));
    }

    /** Changes the User's own password and clears mustChangePassword. */
    public void changePassword(String userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthenticatedException("Your session is no longer valid"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessRuleException("WRONG_PASSWORD", "The current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);
    }
}
