package tn.oliveconnect.resource;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.oliveconnect.common.PageResponse;

/** Logistics manager: list, create, receive. Admin: read only. */
@RestController
@RequestMapping("/api/supply-orders")
@PreAuthorize("hasRole('LOGISTICS_MANAGER')")
public class SupplyController {

    private final SupplyService supplyService;

    public SupplyController(SupplyService supplyService) {
        this.supplyService = supplyService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('LOGISTICS_MANAGER', 'ADMIN')")
    public PageResponse<SupplyOrderDto> list(@RequestParam(required = false) SupplyStatus status,
                                             @RequestParam(defaultValue = "0") @Min(0) int page,
                                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return supplyService.list(status, page, size);
    }

    @PostMapping
    public ResponseEntity<SupplyOrderDto> create(@Valid @RequestBody SupplyOrderCreateDto dto) {
        SupplyOrderDto created = supplyService.create(dto);
        return ResponseEntity.created(URI.create("/api/supply-orders/" + created.id())).body(created);
    }

    @PostMapping("/{id}/receive")
    public SupplyOrderDto receive(@PathVariable String id) {
        return supplyService.receive(id);
    }
}
