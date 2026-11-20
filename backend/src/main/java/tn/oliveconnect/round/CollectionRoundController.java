package tn.oliveconnect.round;

import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rounds")
@PreAuthorize("hasAnyRole('COLLECTION_MANAGER', 'TEAM_LEADER')")
public class CollectionRoundController {

    private final CollectionRoundService roundService;

    public CollectionRoundController(CollectionRoundService roundService) {
        this.roundService = roundService;
    }

    @PostMapping
    @PreAuthorize("hasRole('COLLECTION_MANAGER')")
    public ResponseEntity<CollectionRoundDto> create(@Valid @RequestBody CollectionRoundCreateDto dto,
                                                     Authentication authentication) {
        CollectionRoundDto created = roundService.plan(dto, authentication);
        return ResponseEntity.created(URI.create("/api/rounds/" + created.id())).body(created);
    }

    @GetMapping
    public List<CollectionRoundDto> list(@RequestParam(required = false) LocalDate from,
                                        @RequestParam(required = false) LocalDate to,
                                        @RequestParam(required = false) RoundStatus status,
                                        @RequestParam(required = false) String teamLeaderId,
                                        Authentication authentication) {
        return roundService.list(from, to, status, teamLeaderId, authentication);
    }

    @GetMapping("/{id}")
    public CollectionRoundDto get(@PathVariable String id, Authentication authentication) {
        return roundService.get(id, authentication);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('COLLECTION_MANAGER')")
    public CollectionRoundDto update(@PathVariable String id, @Valid @RequestBody CollectionRoundUpdateDto dto,
                                     Authentication authentication) {
        return roundService.update(id, dto, authentication);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('COLLECTION_MANAGER')")
    public ResponseEntity<Void> cancel(@PathVariable String id,
                                       @Valid @RequestBody(required = false) RoundCancelDto dto,
                                       Authentication authentication) {
        roundService.cancel(id, dto, authentication);
        return ResponseEntity.noContent().build();
    }
}
