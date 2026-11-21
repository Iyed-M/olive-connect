package tn.oliveconnect.dashboard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import tn.oliveconnect.common.FieldValidationException;
import tn.oliveconnect.dashboard.dto.FillRatePoint;
import tn.oliveconnect.dashboard.dto.KpiFilter;
import tn.oliveconnect.dashboard.dto.KpiSummaryDto;
import tn.oliveconnect.dashboard.dto.LeadTimePoint;
import tn.oliveconnect.dashboard.dto.MillVolumePoint;
import tn.oliveconnect.dashboard.dto.YieldPoint;
import tn.oliveconnect.mill.Mill;
import tn.oliveconnect.mill.MillRepository;
import tn.oliveconnect.user.Delegation;
import tn.oliveconnect.user.Role;
import tn.oliveconnect.user.User;
import tn.oliveconnect.user.UserRepository;

@Service
public class KpiService {

    public static final String CACHE_KEY_PREFIX = "cache:kpi::";
    public static final int CACHE_TTL_SECONDS = 300;

    private final KpiRepositoryCustom kpiRepository;
    private final UserRepository userRepository;
    private final MillRepository millRepository;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public KpiService(KpiRepositoryCustom kpiRepository, UserRepository userRepository,
            MillRepository millRepository, StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.kpiRepository = kpiRepository;
        this.userRepository = userRepository;
        this.millRepository = millRepository;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public KpiSummaryDto summary(KpiQuery query, Authentication authentication) {
        KpiFilter filter = filter(query, authentication, true, true);
        return cached("summary", query, filter, new TypeReference<KpiSummaryDto>() {
        },
                () -> kpiRepository.aggregateSummary(filter));
    }

    public List<YieldPoint> yield(KpiQuery query, Authentication authentication) {
        KpiFilter filter = filter(query, authentication, true, false);
        String groupBy = groupBy(query.groupBy(), "orchard", "orchard", "variety", "delegation");
        return cached("yield", withGroupBy(query, groupBy), filter, new TypeReference<>() {
        }, () -> kpiRepository.aggregateYield(filter, groupBy));
    }

    public List<LeadTimePoint> leadTime(KpiQuery query, Authentication authentication) {
        KpiFilter filter = filter(query, authentication, true, false);
        String groupBy = groupBy(query.groupBy(), "delegation", "delegation", "week");
        return cached("lead-time", withGroupBy(query, groupBy), filter, new TypeReference<>() {
        }, () -> kpiRepository.aggregateLeadTime(filter, groupBy));
    }

    public List<FillRatePoint> fillRate(KpiQuery query, Authentication authentication) {
        KpiFilter filter = filter(query, authentication, true, true);
        String groupBy = groupBy(query.groupBy(), "week", "week", "bin", "round");
        return cached("fill-rate", withGroupBy(query, groupBy), filter, new TypeReference<>() {
        }, () -> kpiRepository.aggregateFillRate(filter, groupBy));
    }

    public List<MillVolumePoint> millVolumes(KpiQuery query, Authentication authentication) {
        KpiFilter filter = filter(query, authentication, false, true);
        return cached("mill-volumes", query, filter, new TypeReference<>() {
        }, () -> withCapacityUse(kpiRepository.aggregateMillVolumes(filter)));
    }

    public CsvExport export(String kpi, KpiQuery query, Authentication authentication) {
        String normalized = normalizeKpi(kpi);
        String csv = switch (normalized) {
            case "summary" -> summaryCsv(summary(query, authentication));
            case "yield" -> yieldCsv(this.yield(query, authentication));
            case "lead-time" -> leadTimeCsv(leadTime(query, authentication));
            case "fill-rate" -> fillRateCsv(fillRate(query, authentication));
            case "mill-volumes" -> millVolumesCsv(millVolumes(query, authentication));
            default -> throw new FieldValidationException("kpi", "must be a dashboard KPI");
        };
        return new CsvExport(normalized + ".csv", csv.getBytes(StandardCharsets.UTF_8));
    }

    private KpiFilter filter(KpiQuery query, Authentication authentication, boolean seasonRequired,
            boolean millManagerAllowed) {
        String season = normalizeSeason(query.season(), seasonRequired);
        Instant from = parseTime(query.from(), "from");
        Instant to = parseTime(query.to(), "to");
        if (from != null && to != null && !from.isBefore(to)) {
            throw new FieldValidationException("to", "must be after from");
        }
        String variety = normalizeOptional(query.variety());
        String delegation = normalizeDelegation(query.delegation());
        String millId = normalizeOptional(query.millId());

        if (hasRole(authentication, Role.ADMIN) || hasRole(authentication, Role.COLLECTION_MANAGER)) {
            // A user may hold more than one role; the broader manager role wins over a mill scope.
        } else if (hasRole(authentication, Role.MILL_MANAGER)) {
            if (!millManagerAllowed) {
                throw new AccessDeniedException("This KPI is not available to mill managers");
            }
            User user = userRepository.findById(authentication.getName())
                    .orElseThrow(() -> new AccessDeniedException("Dashboard access requires a valid user"));
            if (user.getAssignedMillId() == null) {
                throw new AccessDeniedException("A mill manager must be assigned to a mill");
            }
            if (millId != null && !Objects.equals(millId, user.getAssignedMillId())) {
                throw new AccessDeniedException("The dashboard is limited to the assigned mill");
            }
            millId = user.getAssignedMillId();
        } else {
            throw new AccessDeniedException("Dashboard access requires a supported role");
        }
        return new KpiFilter(season, from, to, variety, delegation, millId);
    }

    private String normalizeSeason(String season, boolean required) {
        String normalized = normalizeOptional(season);
        if (normalized == null) {
            if (required) {
                throw new FieldValidationException("season", "is required");
            }
            return null;
        }
        if (!normalized.matches("\\d{4}-\\d{4}")) {
            throw new FieldValidationException("season", "must use YYYY-YYYY");
        }
        int start = Integer.parseInt(normalized.substring(0, 4));
        int end = Integer.parseInt(normalized.substring(5));
        if (end != start + 1) {
            throw new FieldValidationException("season", "must contain consecutive years");
        }
        return normalized;
    }

    private Instant parseTime(String value, String field) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        try {
            return Instant.parse(normalized);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(normalized).atStartOfDay(ZoneOffset.UTC).toInstant();
            } catch (DateTimeParseException ex) {
                throw new FieldValidationException(field, "must be an ISO-8601 date or instant");
            }
        }
    }

    private String normalizeDelegation(String delegation) {
        String normalized = normalizeOptional(delegation);
        if (normalized == null) {
            return null;
        }
        try {
            return Delegation.valueOf(normalized.toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException ex) {
            throw new FieldValidationException("delegation", "must be a delegation");
        }
    }

    private String groupBy(String value, String defaultValue, String... allowed) {
        String normalized = value == null || value.isBlank() ? defaultValue : value.trim().toLowerCase(Locale.ROOT);
        if (Arrays.stream(allowed).noneMatch(normalized::equals)) {
            throw new FieldValidationException("groupBy", "must be one of " + String.join(", ", allowed));
        }
        return normalized;
    }

    private String normalizeKpi(String kpi) {
        return kpi == null ? "" : kpi.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private <T> T cached(String name, KpiQuery query, KpiFilter filter, TypeReference<T> type,
            Supplier<T> loader) {
        String key = cacheKey(name, query, filter);
        String cached = read(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, type);
            } catch (JsonProcessingException ex) {
                delete(key);
            }
        }
        T result = loader.get();
        write(key, result);
        return result;
    }

    private KpiQuery withGroupBy(KpiQuery query, String groupBy) {
        return new KpiQuery(query.season(), query.from(), query.to(), query.variety(), query.delegation(),
                query.millId(), groupBy);
    }

    private String cacheKey(String name, KpiQuery query, KpiFilter filter) {
        String canonical = String.join("|", name, nullToEmpty(filter.season()),
                nullToEmpty(filter.from()), nullToEmpty(filter.to()), nullToEmpty(filter.varietyCode()),
                nullToEmpty(filter.delegation()), nullToEmpty(filter.millId()),
                nullToEmpty(query.groupBy()));
        return CACHE_KEY_PREFIX + name + ":" + sha256(canonical);
    }

    private String read(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void write(String key, Object value) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value),
                    java.time.Duration.ofSeconds(CACHE_TTL_SECONDS));
        } catch (JsonProcessingException | RuntimeException ignored) {
            // Redis is an optimization. A cache outage must not make the dashboard unavailable.
        }
    }

    private void delete(String key) {
        try {
            redis.delete(key);
        } catch (RuntimeException ignored) {
            // Ignore a cache cleanup failure; the next read will recompute the KPI.
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private List<MillVolumePoint> withCapacityUse(List<MillVolumePoint> points) {
        Map<String, Mill> mills = new LinkedHashMap<>();
        millRepository.findAllById(points.stream().map(MillVolumePoint::millId).distinct().toList())
                .forEach(mill -> mills.put(mill.getId(), mill));
        return points.stream().map(point -> {
            Mill mill = mills.get(point.millId());
            Double use = mill == null || mill.getDailyCapacityKg() <= 0 ? null
                    : point.totalNetKg() / mill.getDailyCapacityKg() * 100;
            return new MillVolumePoint(point.millId(), point.date(), point.totalNetKg(), point.receptions(),
                    point.qualityDistribution(), use);
        }).toList();
    }

    private boolean hasRole(Authentication authentication, Role role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role.name()));
    }

    private String summaryCsv(KpiSummaryDto summary) {
        return csv(List.of("season", "receptions", "totalNetKg", "avgYieldKgHa", "estOilKg", "avgLeadTimeH",
                        "p90LeadTimeH", "avgFillRate"),
                List.of(List.of(summary.season(), summary.receptions(), summary.totalNetKg(), summary.avgYieldKgHa(),
                        summary.estOilKg(), summary.avgLeadTimeH(), summary.p90LeadTimeH(), summary.avgFillRate())));
    }

    private String yieldCsv(List<YieldPoint> points) {
        var rows = points.stream().map(point -> row(point.key(), point.groupBy(), point.orchardId(),
                point.variety(), point.delegation(), point.areaHa(), point.totalNetKg(), point.estOilKg(),
                point.yieldKgHa())).toList();
        return csv(List.of("key", "groupBy", "orchardId", "variety", "delegation", "areaHa", "totalNetKg",
                "estOilKg", "yieldKgHa"), rows);
    }

    private String leadTimeCsv(List<LeadTimePoint> points) {
        var rows = points.stream().map(point -> row(point.key(), point.delegation(), point.year(),
                point.week(), point.avgLeadTimeH(), point.p90LeadTimeH(), point.n())).toList();
        return csv(List.of("key", "delegation", "year", "week", "avgLeadTimeH", "p90LeadTimeH", "n"), rows);
    }

    private String fillRateCsv(List<FillRatePoint> points) {
        var rows = points.stream().map(point -> row(point.key(), point.year(), point.week(),
                point.avgFillRate(), point.receptions(), point.underFilled(), point.binId(), point.roundId())).toList();
        return csv(List.of("key", "year", "week", "avgFillRate", "receptions", "underFilled", "binId", "roundId"),
                rows);
    }

    private String millVolumesCsv(List<MillVolumePoint> points) {
        var rows = points.stream().map(point -> row(point.millId(), point.date(), point.totalNetKg(),
                point.receptions(), point.qualityDistribution(), point.capacityUsePct())).toList();
        return csv(List.of("millId", "date", "totalNetKg", "receptions", "qualityDistribution", "capacityUsePct"),
                rows);
    }

    private String csv(List<String> headers, List<? extends List<?>> rows) {
        StringBuilder result = new StringBuilder(String.join(",", headers)).append('\n');
        for (List<?> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) {
                    result.append(',');
                }
                result.append(escape(row.get(i)));
            }
            result.append('\n');
        }
        return result.toString();
    }

    private List<Object> row(Object... values) {
        return Arrays.asList(values);
    }

    private String escape(Object value) {
        String text = String.valueOf(value == null ? "" : value);
        return text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")
                ? '"' + text.replace("\"", "\"\"") + '"' : text;
    }

    private String nullToEmpty(Object value) {
        return value == null ? "" : value.toString();
    }
}
