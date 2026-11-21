package tn.oliveconnect.alert;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.mongodb.client.gridfs.model.GridFSFile;
import tn.oliveconnect.common.ConflictException;
import tn.oliveconnect.common.FieldValidationException;
import tn.oliveconnect.common.NotFoundException;
import tn.oliveconnect.common.StatusChange;
import tn.oliveconnect.harvest.HarvestRequest;
import tn.oliveconnect.harvest.HarvestRequestService;
import tn.oliveconnect.notification.NotificationService;
import tn.oliveconnect.notification.NotificationType;
import tn.oliveconnect.orchard.GeoPoint;
import tn.oliveconnect.orchard.GeoPointDto;
import tn.oliveconnect.orchard.Orchard;
import tn.oliveconnect.orchard.OrchardRepository;
import tn.oliveconnect.user.Role;

@Service
@Transactional
public class FieldAlertService {

    private static final double ALERT_DISTANCE_KM = 2.0;
    private static final double EARTH_RADIUS_KM = 6_371.0088;
    private static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;
    private static final List<AlertStatus> OPEN_STATUSES = List.of(AlertStatus.NEW, AlertStatus.ACKNOWLEDGED);
    private static final List<String> IMAGE_TYPES = List.of("image/jpeg", "image/png");

    private final FieldAlertRepository alertRepository;
    private final OrchardRepository orchardRepository;
    private final GridFsTemplate gridFsTemplate;
    private final NotificationService notificationService;
    private final HarvestRequestService harvestRequestService;

    public FieldAlertService(FieldAlertRepository alertRepository, OrchardRepository orchardRepository,
            GridFsTemplate gridFsTemplate, NotificationService notificationService,
            HarvestRequestService harvestRequestService) {
        this.alertRepository = alertRepository;
        this.orchardRepository = orchardRepository;
        this.gridFsTemplate = gridFsTemplate;
        this.notificationService = notificationService;
        this.harvestRequestService = harvestRequestService;
    }

    public FieldAlertDto report(AlertReportDto dto, List<MultipartFile> photos, Authentication authentication) {
        validateReport(dto);
        List<MultipartFile> validPhotos = photos == null ? List.of() : photos.stream()
                .filter(Objects::nonNull)
                .toList();
        validatePhotos(validPhotos);

        String farmerId = authentication.getName();
        Orchard orchard = orchardRepository.findByIdAndOwnerId(dto.orchardId(), farmerId)
                .orElseThrow(() -> new NotFoundException("Orchard not found"));
        if (alertRepository.existsByOrchardIdAndTypeAndStatusIn(orchard.getId(), dto.type(), OPEN_STATUSES)) {
            throw new ConflictException("ACTIVE_ALERT_EXISTS", "An alert of this type is already open for this orchard");
        }

        GeoPoint location = new GeoPoint(dto.location().coordinates().get(0), dto.location().coordinates().get(1));
        FieldAlert alert = new FieldAlert();
        alert.setType(dto.type());
        alert.setSeverity(dto.severity());
        alert.setSource(AlertSource.FARMER);
        alert.setOrchardId(orchard.getId());
        alert.setOrchardName(orchard.getName());
        alert.setFarmerId(farmerId);
        alert.setReporterId(farmerId);
        alert.setOpenAlertKey(FieldAlert.openAlertKey(orchard.getId(), dto.type()));
        alert.setLocation(location);
        alert.setLocationMismatch(distanceKm(location, orchard.getLocation()) > ALERT_DISTANCE_KM);
        alert.setDescription(dto.description());
        alert.getStatusHistory().add(new StatusChange(null, AlertStatus.NEW.name(), Instant.now(), farmerId, null));

        FieldAlert saved;
        try {
            saved = alertRepository.save(alert);
            if (!validPhotos.isEmpty()) {
                saved.setPhotoUrls(storePhotos(saved.getId(), validPhotos));
                saved = alertRepository.save(saved);
            }
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("ACTIVE_ALERT_EXISTS",
                    "An alert of this type is already open for this orchard");
        }
        notificationService.notifyCollectionManagers(NotificationType.NEW_ALERT, "New field alert",
                "A farmer reported an alert for " + saved.getOrchardName(), linkFor(saved));
        return toDto(saved, authentication);
    }

    @Transactional(readOnly = true)
    public List<FieldAlertDto> list(Authentication authentication, AlertStatus status, AlertType type, Boolean mine) {
        List<FieldAlert> alerts;
        if (canReadAll(authentication)) {
            alerts = Boolean.TRUE.equals(mine)
                    ? alertRepository.findByHandledByIdOrderByCreatedAtDesc(authentication.getName())
                    : alertRepository.findAllByOrderByCreatedAtDesc();
        } else {
            alerts = alertRepository.findByFarmerIdOrderByCreatedAtDesc(authentication.getName());
        }
        return alerts.stream()
                .filter(alert -> status == null || alert.getStatus() == status)
                .filter(alert -> type == null || alert.getType() == type)
                .map(alert -> toDto(alert, authentication))
                .toList();
    }

    @Transactional(readOnly = true)
    public FieldAlertDto get(String id, Authentication authentication) {
        return toDto(findVisible(id, authentication), authentication);
    }

    public FieldAlertDto acknowledge(String id, Authentication authentication) {
        return changeStatus(id, AlertStatus.ACKNOWLEDGED, null, authentication);
    }

    public FieldAlertDto resolve(String id, AlertActionDto dto, Authentication authentication) {
        return changeStatus(id, AlertStatus.RESOLVED, dto == null ? null : dto.resolution(), authentication);
    }

    public FieldAlertDto dismiss(String id, AlertActionDto dto, Authentication authentication) {
        return changeStatus(id, AlertStatus.DISMISSED, dto == null ? null : dto.resolution(), authentication);
    }

    public FieldAlertDto createUrgentRequest(String id, Authentication authentication) {
        FieldAlert alert = findForManager(id);
        if (alert.getType() != AlertType.MATURITY) {
            throw new ConflictException("URGENT_REQUEST_NOT_ALLOWED", "Only maturity alerts can create urgent requests");
        }
        if (alert.getStatus() != AlertStatus.ACKNOWLEDGED) {
            throw invalidTransition(alert, AlertStatus.RESOLVED);
        }
        if (alert.getHarvestRequestId() != null) {
            throw new ConflictException("URGENT_REQUEST_EXISTS", "An urgent request was already created for this alert");
        }

        HarvestRequest request = harvestRequestService.createUrgentFromAlert(alert.getOrchardId(), alert.getFarmerId(),
                authentication.getName(), alert.getId());
        alert.setHarvestRequestId(request.getId());
        alert.setHandledById(authentication.getName());
        alert.setResolution("Urgent harvest request created");
        alert.transitionTo(AlertStatus.RESOLVED, authentication.getName(), alert.getResolution());
        FieldAlert saved = alertRepository.save(alert);
        notifyFarmer(saved, NotificationType.ALERT_RESOLVED, "Field alert resolved",
                "An urgent harvest request was created for " + saved.getOrchardName(), linkFor(saved));
        return toDto(saved, authentication);
    }

    @Transactional(readOnly = true)
    public GridFsResource photo(String fileId, Authentication authentication) {
        if (!ObjectId.isValid(fileId)) {
            throw new NotFoundException("Photo not found");
        }
        GridFSFile file = gridFsTemplate.findOne(Query.query(Criteria.where("_id").is(new ObjectId(fileId))));
        if (file == null || file.getMetadata() == null) {
            throw new NotFoundException("Photo not found");
        }
        String alertId = file.getMetadata().getString("alertId");
        if (alertId == null) {
            throw new NotFoundException("Photo not found");
        }
        findVisible(alertId, authentication);
        GridFsResource resource = gridFsTemplate.getResource(file);
        if (!resource.exists()) {
            throw new NotFoundException("Photo not found");
        }
        return resource;
    }

    private FieldAlertDto changeStatus(String id, AlertStatus target, String comment, Authentication authentication) {
        FieldAlert alert = findForManager(id);
        if (!alert.canTransitionTo(target)) {
            throw invalidTransition(alert, target);
        }
        alert.setHandledById(authentication.getName());
        if (comment != null) {
            alert.setResolution(comment);
        }
        alert.transitionTo(target, authentication.getName(), comment);
        FieldAlert saved = alertRepository.save(alert);
        NotificationType notificationType = target == AlertStatus.ACKNOWLEDGED
                ? NotificationType.ALERT_ACKNOWLEDGED
                : target == AlertStatus.RESOLVED ? NotificationType.ALERT_RESOLVED : NotificationType.ALERT_DISMISSED;
        notifyFarmer(saved, notificationType, "Field alert " + target.name().toLowerCase(),
                comment == null ? "Your field alert was updated" : comment, linkFor(saved));
        return toDto(saved, authentication);
    }

    private List<String> storePhotos(String alertId, List<MultipartFile> photos) {
        List<String> urls = new ArrayList<>();
        for (MultipartFile photo : photos) {
            try (InputStream inputStream = photo.getInputStream()) {
                Document metadata = new Document("alertId", alertId)
                        .append("contentType", photo.getContentType());
                ObjectId fileId = gridFsTemplate.store(inputStream, photo.getOriginalFilename(),
                        photo.getContentType(), metadata);
                urls.add("/api/files/alerts/" + fileId);
            } catch (IOException ex) {
                throw new IllegalStateException("Unable to store alert photo", ex);
            }
        }
        return urls;
    }

    private void validateReport(AlertReportDto dto) {
        if (dto == null) {
            throw new FieldValidationException("report", "must be provided");
        }
        if (dto.type() == null) {
            throw new FieldValidationException("type", "must be provided");
        }
        if (dto.orchardId() == null || dto.orchardId().isBlank()) {
            throw new FieldValidationException("orchardId", "must not be blank");
        }
        GeoPointDto location = dto.location();
        if (location == null || location.coordinates() == null || location.coordinates().size() != 2) {
            throw new FieldValidationException("location", "must be a GeoJSON Point with two coordinates");
        }
        if (!"Point".equalsIgnoreCase(location.type())) {
            throw new FieldValidationException("location.type", "must be Point");
        }
        double longitude = location.coordinates().get(0);
        double latitude = location.coordinates().get(1);
        if (!Double.isFinite(longitude) || !Double.isFinite(latitude)
                || longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) {
            throw new FieldValidationException("location.coordinates", "must contain valid coordinates");
        }
    }

    private void validatePhotos(Collection<MultipartFile> photos) {
        for (MultipartFile photo : photos) {
            if (photo.isEmpty()) {
                throw new FieldValidationException("photo", "must not be empty");
            }
            if (photo.getSize() > MAX_PHOTO_BYTES) {
                throw new FieldValidationException("photo", "must not exceed 5 MB");
            }
            if (!IMAGE_TYPES.contains(photo.getContentType())) {
                throw new FieldValidationException("photo", "must be a JPEG or PNG image");
            }
        }
    }

    private FieldAlert findVisible(String id, Authentication authentication) {
        if (canReadAll(authentication)) {
            return alertRepository.findById(id).orElseThrow(() -> new NotFoundException("Field alert not found"));
        }
        return alertRepository.findByIdAndFarmerId(id, authentication.getName())
                .orElseThrow(() -> new NotFoundException("Field alert not found"));
    }

    private FieldAlert findForManager(String id) {
        return alertRepository.findById(id).orElseThrow(() -> new NotFoundException("Field alert not found"));
    }

    private FieldAlertDto toDto(FieldAlert alert, Authentication authentication) {
        return FieldAlertDto.from(alert, allowedActions(alert, authentication));
    }

    private List<String> allowedActions(FieldAlert alert, Authentication authentication) {
        if (!hasRole(authentication, Role.COLLECTION_MANAGER)) {
            return List.of();
        }
        List<String> actions = new ArrayList<>();
        if (alert.getStatus() == AlertStatus.NEW) {
            actions.add("ACKNOWLEDGE");
            actions.add("DISMISS");
        } else if (alert.getStatus() == AlertStatus.ACKNOWLEDGED) {
            actions.add("RESOLVE");
            actions.add("DISMISS");
            if (alert.getType() == AlertType.MATURITY && alert.getHarvestRequestId() == null) {
                actions.add("CREATE_URGENT_REQUEST");
            }
        }
        return List.copyOf(actions);
    }

    private ConflictException invalidTransition(FieldAlert alert, AlertStatus target) {
        return new ConflictException("INVALID_TRANSITION",
                "Cannot transition field alert from " + alert.getStatus() + " to " + target);
    }

    private boolean canReadAll(Authentication authentication) {
        return hasRole(authentication, Role.COLLECTION_MANAGER) || hasRole(authentication, Role.ADMIN);
    }

    private boolean hasRole(Authentication authentication, Role role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role.name()));
    }

    private void notifyFarmer(FieldAlert alert, NotificationType type, String title, String message, String link) {
        if (alert.getFarmerId() != null) {
            notificationService.notifyUser(alert.getFarmerId(), type, title, message, link);
        }
    }

    private String linkFor(FieldAlert alert) {
        return "/alerts/" + alert.getId();
    }

    private double distanceKm(GeoPoint left, GeoPoint right) {
        if (left == null || right == null) {
            return Double.POSITIVE_INFINITY;
        }
        double latitude1 = Math.toRadians(left.latitude());
        double latitude2 = Math.toRadians(right.latitude());
        double deltaLatitude = Math.toRadians(right.latitude() - left.latitude());
        double deltaLongitude = Math.toRadians(right.longitude() - left.longitude());
        double haversine = Math.sin(deltaLatitude / 2) * Math.sin(deltaLatitude / 2)
                + Math.cos(latitude1) * Math.cos(latitude2)
                * Math.sin(deltaLongitude / 2) * Math.sin(deltaLongitude / 2);
        double boundedHaversine = Math.min(1.0, Math.max(0.0, haversine));
        return EARTH_RADIUS_KM * 2
                * Math.atan2(Math.sqrt(boundedHaversine), Math.sqrt(1 - boundedHaversine));
    }
}
