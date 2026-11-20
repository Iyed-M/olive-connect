package tn.oliveconnect.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String COOKIE_NAME = "refresh_token";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        // behind Nginx, getRemoteAddr() is the real client IP thanks to X-Forwarded-For (server.forward-headers-strategy)
        Session session = authService.login(request.email(), request.password(), httpRequest.getRemoteAddr());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken(), RefreshTokenStore.LIFETIME).toString())
                .body(session.body());
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(name = COOKIE_NAME, required = false) String refreshToken) {
        var session = refreshToken == null ? Optional.<Session>empty() : authService.refresh(refreshToken);
        if (session.isEmpty()) {
            // No cookie: nobody is logged in. A cookie that is refused: SESSION_EXPIRED, which tells the
            // frontend that another tab may have just rotated it, so a retry can succeed.
            String code = refreshToken == null ? "UNAUTHENTICATED" : "SESSION_EXPIRED";
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Session expired");
            problem.setProperty("code", code);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                    .body(problem);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        refreshCookie(session.get().refreshToken(), RefreshTokenStore.LIFETIME).toString())
                .body(session.get().body());
    }

    // public and identified by the cookie only, so it works even when the access token has expired
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = COOKIE_NAME, required = false) String refreshToken) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .build();
    }

    /** The one place that defines the cookie attributes (docs/06 section 4.1). Duration.ZERO clears it. */
    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
    }
}
