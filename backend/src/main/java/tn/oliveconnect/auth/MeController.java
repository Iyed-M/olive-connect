package tn.oliveconnect.auth;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.oliveconnect.user.MeDto;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final AuthService authService;

    public MeController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    public MeDto me(@AuthenticationPrincipal Jwt jwt) {
        return authService.me(jwt.getSubject());   // sub = user id
    }

    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody ChangePasswordDto dto) {
        authService.changePassword(jwt.getSubject(), dto.currentPassword(), dto.newPassword());
        return ResponseEntity.noContent().build();
    }
}
