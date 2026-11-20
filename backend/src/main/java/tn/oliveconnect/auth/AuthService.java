package tn.oliveconnect.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import tn.oliveconnect.common.NotFoundException;
import tn.oliveconnect.user.MeDto;
import tn.oliveconnect.user.User;
import tn.oliveconnect.user.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(String email, String password) {
        // every failure throws the same exception: the caller cannot tell which check failed
        User user = userRepository.findByEmail(email.trim().toLowerCase())
                .filter(User::isEnabled)
                .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        AccessToken token = jwtService.accessToken(user);
        return new LoginResponse(token.value(), token.expiresIn(), MeDto.from(user));
    }

    public MeDto me(String userId) {
        return userRepository.findById(userId)
                .map(MeDto::from)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
