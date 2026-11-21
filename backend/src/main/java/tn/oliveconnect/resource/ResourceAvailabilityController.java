package tn.oliveconnect.resource;

import java.time.Instant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.oliveconnect.common.FieldValidationException;

@RestController
@RequestMapping("/api/resources")
@PreAuthorize("hasAnyRole('COLLECTION_MANAGER', 'LOGISTICS_MANAGER', 'ADMIN')")
public class ResourceAvailabilityController {

    private final ResourceAvailabilityService availabilityService;

    public ResourceAvailabilityController(ResourceAvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping("/availability")
    public ResourceAvailabilityDto availability(@RequestParam Instant start, @RequestParam Instant end,
                                                @RequestParam(required = false) String type) {
        if (!end.isAfter(start)) {
            throw new FieldValidationException("end", "must be after start");
        }
        return availabilityService.find(start, end, type);
    }
}
