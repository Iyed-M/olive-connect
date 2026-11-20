package tn.oliveconnect.map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPolygon;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tn.oliveconnect.common.FieldValidationException;
import tn.oliveconnect.orchard.GeoPoint;
import tn.oliveconnect.orchard.HarvestStatus;
import tn.oliveconnect.orchard.Orchard;
import tn.oliveconnect.user.Delegation;

@Service
public class MapService {

    static final Duration CACHE_TTL = Duration.ofSeconds(60);
    /** From this zoom on, every orchard is its own marker. Below, orchards are grouped in a grid. */
    static final int CLUSTER_BELOW_ZOOM = 13;
    static final int DEFAULT_ZOOM = 15;
    static final int MAX_ZOOM = 22;
    /** Safety limit for a very large area with no clustering. */
    static final int MAX_ORCHARDS = 5000;
    /** Largest bbox accepted, in degrees (about 220 km). The Sfax region needs far less. */
    static final double MAX_BBOX_DEGREES = 4;

    private final MongoTemplate mongoTemplate;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public MapService(MongoTemplate mongoTemplate, StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.mongoTemplate = mongoTemplate;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /** Returns the GeoJSON FeatureCollection as JSON text, from Redis when the same request was made in the last minute. */
    public String publicMap(String bbox, Integer zoom, String variety, String status,
            String delegation, Double maturityMin, Double maturityMax) {
        double[] box = parseBbox(bbox);
        int z = zoom == null ? DEFAULT_ZOOM : zoom;
        if (z < 0 || z > MAX_ZOOM) {
            throw new FieldValidationException("zoom", "must be between 0 and " + MAX_ZOOM);
        }
        HarvestStatus harvestStatus = parseStatus(status);
        String delegationCode = parseDelegation(delegation);
        String varietyCode = variety == null || variety.isBlank() ? null : variety.trim().toUpperCase(Locale.ROOT);
        checkFinite("maturityMin", maturityMin);
        checkFinite("maturityMax", maturityMax);

        // The key is built from parsed and normalised values, so equal requests share one entry.
        String key = String.format(Locale.ROOT, "cache:mapPublic::%s,%s,%s,%s:%d:%s:%s:%s:%s:%s",
                box[0], box[1], box[2], box[3], z, varietyCode, harvestStatus, delegationCode, maturityMin,
                maturityMax);
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            return cached;
        }

        List<Orchard> orchards = mongoTemplate.find(query(box, varietyCode, harvestStatus, delegationCode, maturityMin, maturityMax),
                Orchard.class);
        List<PublicOrchardFeature> features = z < CLUSTER_BELOW_ZOOM ? cluster(orchards, z) : markers(orchards);
        String json = toJson(new PublicFeatureCollection(features));
        redis.opsForValue().set(key, json, CACHE_TTL);
        return json;
    }

    /** The area filter of the map query, shared with the index test so it explains the very same query. */
    static Criteria areaCriteria(double[] box) {
        GeoJsonPolygon area = new GeoJsonPolygon(
                new Point(box[0], box[1]), new Point(box[2], box[1]),
                new Point(box[2], box[3]), new Point(box[0], box[3]), new Point(box[0], box[1]));
        return Criteria.where("location").within(area);
    }

    private Query query(double[] box, String variety, HarvestStatus status, String delegation, Double min, Double max) {
        List<Criteria> filters = new ArrayList<>();
        filters.add(areaCriteria(box));
        if (variety != null) {
            filters.add(Criteria.where("mainVarietyCode").is(variety));
        }
        if (delegation != null) {
            filters.add(Criteria.where("delegation").is(delegation));
        }
        if (status != null) {
            filters.add(Criteria.where("harvestStatus").is(status));
        }
        if (min != null) {
            filters.add(Criteria.where("currentMaturityIndex").gte(min));
        }
        if (max != null) {
            filters.add(Criteria.where("currentMaturityIndex").lte(max));
        }
        Query query = new Query(new Criteria().andOperator(filters)).limit(MAX_ORCHARDS);
        // Projection: owner, ownerId and yield are never read from MongoDB for the public map.
        query.fields().include("location", "mainVarietyCode", "harvestStatus", "currentMaturityIndex");
        return query;
    }

    private List<PublicOrchardFeature> markers(List<Orchard> orchards) {
        return orchards.stream()
                .map(o -> PublicOrchardFeature.orchard(o.getId(), o.getLocation(), o.getMainVarietyCode(),
                        o.getHarvestStatus(), o.getCurrentMaturityIndex()))
                .toList();
    }

    /** Grid clustering: one cell is 1/4 of a map tile at this zoom. A cluster sits at the average of its orchards. */
    private List<PublicOrchardFeature> cluster(List<Orchard> orchards, int zoom) {
        double cell = 360.0 / (1L << zoom) / 4;
        Map<String, List<Orchard>> cells = new LinkedHashMap<>();
        for (Orchard orchard : orchards) {
            String cellKey = (long) Math.floor(orchard.getLocation().longitude() / cell) + ":"
                    + (long) Math.floor(orchard.getLocation().latitude() / cell);
            cells.computeIfAbsent(cellKey, k -> new ArrayList<>()).add(orchard);
        }
        List<PublicOrchardFeature> features = new ArrayList<>();
        for (List<Orchard> group : cells.values()) {
            if (group.size() == 1) {
                features.addAll(markers(group));
                continue;
            }
            double lng = group.stream().mapToDouble(o -> o.getLocation().longitude()).average().orElse(0);
            double lat = group.stream().mapToDouble(o -> o.getLocation().latitude()).average().orElse(0);
            features.add(PublicOrchardFeature.cluster(new GeoPoint(lng, lat), group.size()));
        }
        return features;
    }

    /** bbox = west,south,east,north in degrees. */
    static double[] parseBbox(String bbox) {
        String[] parts = bbox.split(",");
        if (parts.length != 4) {
            throw new FieldValidationException("bbox", "must be west,south,east,north");
        }
        double[] box = new double[4];
        try {
            for (int i = 0; i < 4; i++) {
                box[i] = Double.parseDouble(parts[i].trim());
            }
        } catch (NumberFormatException ex) {
            throw new FieldValidationException("bbox", "must contain 4 numbers");
        }
        boolean valid = box[0] >= -180 && box[2] <= 180 && box[1] >= -90 && box[3] <= 90
                && box[0] < box[2] && box[1] < box[3];
        if (!valid) {
            throw new FieldValidationException("bbox", "must be a valid area: west < east, south < north");
        }
        if (box[2] - box[0] > MAX_BBOX_DEGREES || box[3] - box[1] > MAX_BBOX_DEGREES) {
            throw new FieldValidationException("bbox", "must not be wider or taller than " + (int) MAX_BBOX_DEGREES
                    + " degrees");
        }
        return box;
    }

    private HarvestStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return HarvestStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new FieldValidationException("status", "must be a harvest status");
        }
    }

    private String parseDelegation(String delegation) {
        if (delegation == null || delegation.isBlank()) {
            return null;
        }
        try {
            return Delegation.valueOf(delegation.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException ex) {
            throw new FieldValidationException("delegation", "must be a delegation");
        }
    }

    private void checkFinite(String field, Double value) {
        if (value != null && !Double.isFinite(value)) {
            throw new FieldValidationException(field, "must be a number");
        }
    }

    private String toJson(PublicFeatureCollection collection) {
        try {
            return objectMapper.writeValueAsString(collection);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
