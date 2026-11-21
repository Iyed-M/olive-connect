package tn.oliveconnect.mill;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.oliveconnect.round.CollectionRoundDto;

@RestController
@RequestMapping("/api/mills")
@PreAuthorize("isAuthenticated()")
public class MillController {

    private final MillService millService;
    private final MillReceptionService millReceptionService;

    public MillController(MillService millService, MillReceptionService millReceptionService) {
        this.millService = millService;
        this.millReceptionService = millReceptionService;
    }

    @GetMapping
    public List<MillDto> list() {
        return millService.list();
    }

    @PutMapping("/{id}/capacity")
    @PreAuthorize("hasAnyRole('MILL_MANAGER', 'ADMIN')")
    public MillDto publishCapacity(@PathVariable String id, @Valid @RequestBody MillCapacityDto dto,
                                   Authentication authentication) {
        return millReceptionService.publishCapacity(id, dto, authentication);
    }

    @GetMapping("/{id}/incoming")
    @PreAuthorize("hasRole('MILL_MANAGER')")
    public List<CollectionRoundDto> incoming(@PathVariable String id, Authentication authentication) {
        return millReceptionService.incoming(id, authentication);
    }

    @PostMapping("/{id}/receptions")
    @PreAuthorize("hasRole('MILL_MANAGER')")
    public ResponseEntity<MillReceptionDto> record(@PathVariable String id,
                                                   @Valid @RequestBody MillReceptionCreateDto dto,
                                                   Authentication authentication) {
        MillReceptionDto reception = millReceptionService.record(id, dto, authentication);
        return ResponseEntity.created(URI.create("/api/receptions/" + reception.id())).body(reception);
    }

}
