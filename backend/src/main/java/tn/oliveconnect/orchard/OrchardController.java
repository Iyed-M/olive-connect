package tn.oliveconnect.orchard;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orchards")
public class OrchardController {

    private final OrchardService orchardService;

    public OrchardController(OrchardService orchardService) {
        this.orchardService = orchardService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('FARMER', 'COLLECTION_MANAGER', 'LOGISTICS_MANAGER', 'ADMIN')")
    public List<OrchardDto> list(Authentication authentication) {
        return orchardService.list(authentication);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('FARMER', 'ADMIN')")
    public ResponseEntity<OrchardDto> create(@Valid @RequestBody OrchardWriteDto dto, Authentication authentication) {
        OrchardDto created = orchardService.create(dto, authentication);
        return ResponseEntity.created(URI.create("/api/orchards/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FARMER', 'COLLECTION_MANAGER', 'LOGISTICS_MANAGER', 'ADMIN')")
    public OrchardDto get(@PathVariable String id, Authentication authentication) {
        return orchardService.get(id, authentication);
    }

    @GetMapping("/nearest")
    @PreAuthorize("hasRole('FARMER')")
    public OrchardDto nearest(@RequestParam double lng, @RequestParam double lat,
                              Authentication authentication) {
        return orchardService.nearest(lng, lat, authentication);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('FARMER', 'COLLECTION_MANAGER', 'LOGISTICS_MANAGER', 'ADMIN')")
    public OrchardDto update(@PathVariable String id, @Valid @RequestBody OrchardWriteDto dto,
                             Authentication authentication) {
        return orchardService.update(id, dto, authentication);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('FARMER', 'ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id, Authentication authentication) {
        orchardService.delete(id, authentication);
        return ResponseEntity.noContent().build();
    }
}
