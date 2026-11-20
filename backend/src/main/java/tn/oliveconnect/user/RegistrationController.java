package tn.oliveconnect.user;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/registrations")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    /** Public (permitAll in SecurityConfig). */
    @PostMapping
    public ResponseEntity<RegistrationDto> create(@Valid @RequestBody RegistrationCreateDto dto) {
        RegistrationDto created = registrationService.create(dto);
        return ResponseEntity.created(URI.create("/api/registrations/" + created.id())).body(created);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<RegistrationDto> list(@RequestParam(required = false) RegistrationStatus status) {
        return registrationService.list(status);
    }

    // Authentication.getName() is the JWT "sub", i.e. the id of the acting Admin
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RegistrationApprovalDto> approve(@PathVariable String id,
                                                           @Valid @RequestBody RegistrationApproveDto dto,
                                                           Authentication auth) {
        // The response holds the temporary password: it must never be stored by a cache.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(registrationService.approve(id, dto, auth.getName()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public RegistrationDto reject(@PathVariable String id, Authentication auth) {
        return registrationService.reject(id, auth.getName());
    }
}
