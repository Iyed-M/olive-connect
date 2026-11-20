package tn.oliveconnect.resource;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.oliveconnect.common.PageResponse;

/** Logistics manager and Admin: full CRUD. Collection manager: read only. Everyone else: 403. */
@RestController
@RequestMapping("/api/equipment")
@PreAuthorize("hasAnyRole('LOGISTICS_MANAGER', 'ADMIN')")
public class EquipmentController {

    private final ResourceService resourceService;

    public EquipmentController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('LOGISTICS_MANAGER', 'COLLECTION_MANAGER', 'ADMIN')")
    public PageResponse<EquipmentDto> list(@RequestParam(required = false) EquipmentType type,
                                           @RequestParam(required = false) EquipmentStatus status,
                                           @RequestParam(defaultValue = "0") @Min(0) int page,
                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return resourceService.listEquipment(type, status, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('LOGISTICS_MANAGER', 'COLLECTION_MANAGER', 'ADMIN')")
    public EquipmentDto get(@PathVariable String id) {
        return resourceService.getEquipment(id);
    }

    @PostMapping
    public ResponseEntity<EquipmentDto> create(@Valid @RequestBody EquipmentRequestDto dto) {
        EquipmentDto created = resourceService.createEquipment(dto);
        return ResponseEntity.created(URI.create("/api/equipment/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public EquipmentDto update(@PathVariable String id, @Valid @RequestBody EquipmentRequestDto dto) {
        return resourceService.updateEquipment(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        resourceService.deleteEquipment(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/unavailabilities")
    public ResponseEntity<EquipmentDto> addUnavailability(@PathVariable String id,
                                                          @Valid @RequestBody UnavailabilityDto dto) {
        return ResponseEntity.status(201).body(resourceService.addEquipmentUnavailability(id, dto));
    }
}
