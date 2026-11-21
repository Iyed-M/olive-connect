package tn.oliveconnect.alert;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tn.oliveconnect.orchard.GeoPointDto;

/** JSON part of a farmer's multipart field-alert report. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlertReportDto(
        @NotNull AlertType type,
        @NotBlank String orchardId,
        @NotNull GeoPointDto location,
        @Size(max = 1000) String description,
        AlertSeverity severity) {

    public AlertReportDto {
        orchardId = orchardId == null ? null : orchardId.trim();
        description = description == null || description.isBlank() ? null : description.trim();
        severity = severity == null ? AlertSeverity.MEDIUM : severity;
    }
}
