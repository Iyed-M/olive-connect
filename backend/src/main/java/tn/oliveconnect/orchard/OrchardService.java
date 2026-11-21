package tn.oliveconnect.orchard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.NearQuery;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import tn.oliveconnect.common.FieldValidationException;
import tn.oliveconnect.common.NotFoundException;
import tn.oliveconnect.user.Delegation;
import tn.oliveconnect.user.Role;
import tn.oliveconnect.user.User;
import tn.oliveconnect.user.UserRepository;

@Service
public class OrchardService {

    public static final String VARIETY_CACHE_KEY = "cache:varieties::all";
    private static final double MIN_TUNISIA_LONGITUDE = 7.4;
    private static final double MAX_TUNISIA_LONGITUDE = 11.7;
    private static final double MIN_TUNISIA_LATITUDE = 30.1;
    private static final double MAX_TUNISIA_LATITUDE = 37.7;

    private final OrchardRepository orchardRepository;
    private final OliveVarietyRepository varietyRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final MongoTemplate mongoTemplate;

    public OrchardService(OrchardRepository orchardRepository, OliveVarietyRepository varietyRepository,
                          UserRepository userRepository, StringRedisTemplate redis, ObjectMapper objectMapper,
                          MongoTemplate mongoTemplate) {
        this.orchardRepository = orchardRepository;
        this.varietyRepository = varietyRepository;
        this.userRepository = userRepository;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.mongoTemplate = mongoTemplate;
    }

    public List<OrchardDto> list(Authentication authentication) {
        List<Orchard> orchards = canReadAll(authentication)
                ? orchardRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
                : orchardRepository.findAllByOwnerIdOrderByNameAsc(authentication.getName());
        return orchards.stream().map(OrchardDto::from).toList();
    }

    public OrchardDto get(String id, Authentication authentication) {
        return OrchardDto.from(findVisible(id, authentication));
    }

    public OrchardDto nearest(double longitude, double latitude, Authentication authentication) {
        validateCoordinates(longitude, latitude);
        NearQuery query = NearQuery.near(new Point(longitude, latitude), Metrics.KILOMETERS)
                .spherical(true)
                .query(Query.query(Criteria.where("ownerId").is(authentication.getName())))
                .limit(1);
        GeoResults<Orchard> results = mongoTemplate.geoNear(query, Orchard.class);
        Orchard nearest = results.getContent().stream()
                .findFirst()
                .map(result -> result.getContent())
                .orElseThrow(() -> new NotFoundException("Orchard not found"));
        return OrchardDto.from(nearest);
    }

    public OrchardDto create(OrchardWriteDto dto, Authentication authentication) {
        String ownerId = ownerIdForCreate(dto, authentication);
        User owner = findOwner(ownerId);
        Orchard orchard = new Orchard();
        orchard.setOwnerId(ownerId);
        apply(orchard, dto, owner);
        return OrchardDto.from(orchardRepository.save(orchard));
    }

    public OrchardDto update(String id, OrchardWriteDto dto, Authentication authentication) {
        Orchard orchard = findVisible(id, authentication);
        User owner = findOwner(orchard.getOwnerId());
        apply(orchard, dto, owner);
        return OrchardDto.from(orchardRepository.save(orchard));
    }

    public void delete(String id, Authentication authentication) {
        Orchard orchard = findVisible(id, authentication);
        orchardRepository.delete(orchard);
    }

    public List<OliveVarietyDto> listVarieties() {
        String cached = readVarietyCache();
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<>() {
                });
            } catch (JsonProcessingException ignored) {
                deleteVarietyCache();
            }
        }

        List<OliveVarietyDto> varieties = varietyRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
                .stream().map(OliveVarietyDto::from).toList();
        try {
            redis.opsForValue().set(VARIETY_CACHE_KEY, objectMapper.writeValueAsString(varieties));
        } catch (JsonProcessingException | RuntimeException ignored) {
            // A cache serialization failure must not make the reference endpoint unavailable.
        }
        return varieties;
    }

    private String readVarietyCache() {
        try {
            return redis.opsForValue().get(VARIETY_CACHE_KEY);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void deleteVarietyCache() {
        try {
            redis.delete(VARIETY_CACHE_KEY);
        } catch (RuntimeException ignored) {
            // Redis is an optimization, never the source of truth for reference data.
        }
    }

    private Orchard findVisible(String id, Authentication authentication) {
        if (canReadAll(authentication)) {
            return orchardRepository.findById(id)
                    .orElseThrow(() -> new NotFoundException("Orchard not found"));
        }
        return orchardRepository.findByIdAndOwnerId(id, authentication.getName())
                .orElseThrow(() -> new NotFoundException("Orchard not found"));
    }

    private boolean canReadAll(Authentication authentication) {
        return hasRole(authentication, Role.COLLECTION_MANAGER) || hasRole(authentication, Role.ADMIN);
    }

    private String ownerIdForCreate(OrchardWriteDto dto, Authentication authentication) {
        if (hasRole(authentication, Role.ADMIN) && dto.ownerId() != null && !dto.ownerId().isBlank()) {
            return dto.ownerId().trim();
        }
        return authentication.getName();
    }

    private User findOwner(String ownerId) {
        return userRepository.findById(ownerId).orElseThrow(() -> new NotFoundException("Orchard owner not found"));
    }

    private void apply(Orchard orchard, OrchardWriteDto dto, User owner) {
        validateLocation(dto.location());
        validatePlots(dto.plots());
        orchard.setName(dto.name().trim());
        orchard.setLocation(dto.location().toEntity());
        orchard.setPlots(dto.plots().stream().map(PlotDto::toEntity).toList());
        orchard.recomputeTotals();
        orchard.setOwner(snapshot(owner));
        orchard.setDelegation(resolveDelegation(dto.delegation(), owner));
    }

    private Map<String, String> snapshot(User owner) {
        String firstName = owner.getFirstName() == null ? "" : owner.getFirstName().trim();
        String lastName = owner.getLastName() == null ? "" : owner.getLastName().trim();
        String displayName = (firstName + " " + lastName).trim();
        return new LinkedHashMap<>(Map.of("displayName", displayName));
    }

    private String resolveDelegation(String requested, User owner) {
        String delegation = requested;
        if (delegation == null || delegation.isBlank()) {
            if (owner.getFarmerProfile() != null && owner.getFarmerProfile().getDelegation() != null) {
                delegation = owner.getFarmerProfile().getDelegation().name();
            } else {
                throw new FieldValidationException("delegation", "is required for an orchard");
            }
        }
        String normalized = delegation.trim().toUpperCase(Locale.ROOT);
        try {
            Delegation.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new FieldValidationException("delegation", "must be a valid Sfax delegation");
        }
        return normalized;
    }

    private void validateLocation(GeoPointDto location) {
        if (!"Point".equalsIgnoreCase(location.type())) {
            throw new FieldValidationException("location.type", "must be Point");
        }
        double longitude = location.coordinates().get(0);
        double latitude = location.coordinates().get(1);
        if (!Double.isFinite(longitude) || !Double.isFinite(latitude)
                || longitude < MIN_TUNISIA_LONGITUDE || longitude > MAX_TUNISIA_LONGITUDE
                || latitude < MIN_TUNISIA_LATITUDE || latitude > MAX_TUNISIA_LATITUDE) {
            throw new FieldValidationException("location.coordinates", "must be a position inside Tunisia");
        }
    }

    private void validateCoordinates(double longitude, double latitude) {
        if (!Double.isFinite(longitude) || !Double.isFinite(latitude)
                || longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) {
            throw new FieldValidationException("coordinates", "must contain valid coordinates");
        }
    }

    private void validatePlots(List<PlotDto> plots) {
        List<String> codes = plots.stream().map(plot -> plot.code().trim().toUpperCase(Locale.ROOT)).toList();
        if (codes.stream().distinct().count() != codes.size()) {
            throw new FieldValidationException("plots", "plot codes must be unique");
        }
        for (int i = 0; i < plots.size(); i++) {
            String code = plots.get(i).varietyCode().trim().toUpperCase(Locale.ROOT);
            if (!varietyRepository.existsById(code)) {
                throw new FieldValidationException("plots[" + i + "].varietyCode", "must be a known olive variety");
            }
        }
    }

    private boolean hasRole(Authentication authentication, Role role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role.name()));
    }
}
