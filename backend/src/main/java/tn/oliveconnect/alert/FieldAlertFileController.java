package tn.oliveconnect.alert;

import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files/alerts")
public class FieldAlertFileController {

    private final FieldAlertService alertService;

    public FieldAlertFileController(FieldAlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping("/{fileId}")
    @PreAuthorize("hasAnyRole('FARMER', 'COLLECTION_MANAGER', 'ADMIN')")
    public ResponseEntity<Resource> get(@PathVariable String fileId, Authentication authentication) {
        GridFsResource resource = alertService.photo(fileId, authentication);
        MediaType contentType = contentType(resource.getContentType());
        return ResponseEntity.ok().contentType(contentType).body(resource);
    }

    private MediaType contentType(String value) {
        try {
            return value == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(value);
        } catch (IllegalArgumentException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
