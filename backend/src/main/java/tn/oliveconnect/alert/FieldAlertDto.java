package tn.oliveconnect.alert;

import java.time.Instant;
import java.util.List;
import tn.oliveconnect.common.StatusChange;
import tn.oliveconnect.orchard.GeoPointDto;

public record FieldAlertDto(
        String id,
        AlertType type,
        AlertSeverity severity,
        AlertStatus status,
        AlertSource source,
        String orchardId,
        String orchardName,
        String farmerId,
        String reporterId,
        GeoPointDto location,
        boolean locationMismatch,
        String description,
        List<String> photoUrls,
        String resolution,
        String handledById,
        String harvestRequestId,
        List<StatusChange> history,
        Instant createdAt,
        List<String> allowedActions) {

    static FieldAlertDto from(FieldAlert alert, List<String> allowedActions) {
        return new FieldAlertDto(alert.getId(), alert.getType(), alert.getSeverity(), alert.getStatus(),
                alert.getSource(), alert.getOrchardId(), alert.getOrchardName(), alert.getFarmerId(),
                alert.getReporterId(), GeoPointDto.from(alert.getLocation()), alert.isLocationMismatch(),
                alert.getDescription(), List.copyOf(alert.getPhotoUrls()), alert.getResolution(),
                alert.getHandledById(), alert.getHarvestRequestId(), List.copyOf(alert.getStatusHistory()),
                alert.getCreatedAt(), List.copyOf(allowedActions));
    }
}
