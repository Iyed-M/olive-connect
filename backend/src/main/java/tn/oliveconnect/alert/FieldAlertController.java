package tn.oliveconnect.alert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tn.oliveconnect.common.FieldValidationException;

@RestController
@RequestMapping("/api/alerts")
public class FieldAlertController {

    private final FieldAlertService alertService;
    private final ObjectMapper objectMapper;

    public FieldAlertController(FieldAlertService alertService, ObjectMapper objectMapper) {
        this.alertService = alertService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('FARMER')")
    public ResponseEntity<FieldAlertDto> report(
            @RequestPart(value = "data", required = false) String data,
            @RequestPart(value = "alert", required = false) String alert,
            @RequestPart(value = "report", required = false) String report,
            @RequestPart(value = "payload", required = false) String payload,
            @RequestPart(value = "photo", required = false) List<MultipartFile> photo,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos,
            Authentication authentication) {
        AlertReportDto dto = parseReport(firstNonBlank(data, alert, report, payload));
        List<MultipartFile> files = new ArrayList<>();
        if (photo != null) {
            files.addAll(photo);
        }
        if (photos != null) {
            files.addAll(photos);
        }
        FieldAlertDto created = alertService.report(dto, files, authentication);
        return ResponseEntity.created(URI.create("/api/alerts/" + created.id())).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('FARMER', 'COLLECTION_MANAGER', 'ADMIN')")
    public List<FieldAlertDto> list(@RequestParam(required = false) AlertStatus status,
                                    @RequestParam(required = false) AlertType type,
                                    @RequestParam(required = false) Boolean mine,
                                    Authentication authentication) {
        return alertService.list(authentication, status, type, mine);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FARMER', 'COLLECTION_MANAGER', 'ADMIN')")
    public FieldAlertDto get(@PathVariable String id, Authentication authentication) {
        return alertService.get(id, authentication);
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasRole('COLLECTION_MANAGER')")
    public FieldAlertDto acknowledge(@PathVariable String id, Authentication authentication) {
        return alertService.acknowledge(id, authentication);
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasRole('COLLECTION_MANAGER')")
    public FieldAlertDto resolve(@PathVariable String id, @Valid @RequestBody(required = false) AlertActionDto dto,
                                 Authentication authentication) {
        return alertService.resolve(id, dto, authentication);
    }

    @PostMapping("/{id}/dismiss")
    @PreAuthorize("hasRole('COLLECTION_MANAGER')")
    public FieldAlertDto dismiss(@PathVariable String id, @Valid @RequestBody(required = false) AlertActionDto dto,
                                 Authentication authentication) {
        return alertService.dismiss(id, dto, authentication);
    }

    @PostMapping("/{id}/create-urgent-request")
    @PreAuthorize("hasRole('COLLECTION_MANAGER')")
    public FieldAlertDto createUrgentRequest(@PathVariable String id, Authentication authentication) {
        return alertService.createUrgentRequest(id, authentication);
    }

    private AlertReportDto parseReport(String json) {
        if (json == null || json.isBlank()) {
            throw new FieldValidationException("report", "must be provided as JSON multipart data");
        }
        try {
            return objectMapper.readValue(json, AlertReportDto.class);
        } catch (JsonProcessingException ex) {
            throw new FieldValidationException("report", "must be valid JSON");
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
