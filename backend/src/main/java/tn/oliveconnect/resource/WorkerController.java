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
@RequestMapping("/api/workers")
@PreAuthorize("hasAnyRole('LOGISTICS_MANAGER', 'ADMIN')")
public class WorkerController {

    private final ResourceService resourceService;

    public WorkerController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('LOGISTICS_MANAGER', 'COLLECTION_MANAGER', 'ADMIN')")
    public PageResponse<WorkerDto> list(@RequestParam(required = false) Boolean active,
                                        @RequestParam(defaultValue = "0") @Min(0) int page,
                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return resourceService.listWorkers(active, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('LOGISTICS_MANAGER', 'COLLECTION_MANAGER', 'ADMIN')")
    public WorkerDto get(@PathVariable String id) {
        return resourceService.getWorker(id);
    }

    @PostMapping
    public ResponseEntity<WorkerDto> create(@Valid @RequestBody WorkerRequestDto dto) {
        WorkerDto created = resourceService.createWorker(dto);
        return ResponseEntity.created(URI.create("/api/workers/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public WorkerDto update(@PathVariable String id, @Valid @RequestBody WorkerRequestDto dto) {
        return resourceService.updateWorker(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        resourceService.deleteWorker(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/unavailabilities")
    public ResponseEntity<WorkerDto> addUnavailability(@PathVariable String id,
                                                       @Valid @RequestBody UnavailabilityDto dto) {
        return ResponseEntity.status(201).body(resourceService.addWorkerUnavailability(id, dto));
    }
}
