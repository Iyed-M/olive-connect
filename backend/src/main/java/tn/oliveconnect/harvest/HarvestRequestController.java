package tn.oliveconnect.harvest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
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
import tn.oliveconnect.common.PageResponse;

@RestController
@RequestMapping("/api/harvest-requests")
public class HarvestRequestController {

    private final HarvestRequestService harvestRequestService;

    public HarvestRequestController(HarvestRequestService harvestRequestService) {
        this.harvestRequestService = harvestRequestService;
    }

    @PostMapping
    @PreAuthorize("hasRole('FARMER')")
    public ResponseEntity<HarvestRequestDto> create(@Valid @RequestBody HarvestRequestCreateDto dto,
                                                    Authentication authentication) {
        HarvestRequestDto created = harvestRequestService.create(dto, authentication);
        return ResponseEntity.created(URI.create("/api/harvest-requests/" + created.id())).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('FARMER', 'COLLECTION_MANAGER')")
    public PageResponse<HarvestRequestDto> list(@RequestParam(required = false) HarvestRequestStatus status,
                                        @RequestParam(required = false) HarvestType type,
                                        @RequestParam(required = false) Boolean mine,
                                        @RequestParam(defaultValue = "0") @Min(0) int page,
                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                        Authentication authentication) {
        return harvestRequestService.list(authentication, status, type, mine, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FARMER', 'COLLECTION_MANAGER', 'TEAM_LEADER')")
    public HarvestRequestDto get(@PathVariable String id, Authentication authentication) {
        return harvestRequestService.get(id, authentication);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('FARMER')")
    public HarvestRequestDto update(@PathVariable String id, @Valid @RequestBody HarvestRequestUpdateDto dto,
                                    Authentication authentication) {
        return harvestRequestService.update(id, dto, authentication);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('FARMER')")
    public HarvestRequestDto cancel(@PathVariable String id,
                                    @Valid @RequestBody(required = false) HarvestRequestCancelDto dto,
                                    Authentication authentication) {
        return harvestRequestService.cancel(id, dto, authentication);
    }

    @PostMapping("/{id}/validate")
    @PreAuthorize("hasRole('COLLECTION_MANAGER')")
    public HarvestRequestDto validate(@PathVariable String id,
                                      @RequestBody(required = false) HarvestRequestVersionDto dto,
                                      Authentication authentication) {
        return harvestRequestService.validate(id, dto, authentication);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('COLLECTION_MANAGER')")
    public HarvestRequestDto reject(@PathVariable String id,
                                    @Valid @RequestBody(required = false) HarvestRequestRejectDto dto,
                                    Authentication authentication) {
        return harvestRequestService.reject(id, dto, authentication);
    }
}
