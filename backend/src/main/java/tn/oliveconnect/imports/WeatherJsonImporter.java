package tn.oliveconnect.imports;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import tn.oliveconnect.user.Delegation;

/** weather_YYYYMMDDHHmm.json (docs/07 §2): { "readings": [ { stationId, delegation, timestamp, ... } ] }. */
@Component
public class WeatherJsonImporter implements DatasetImporter<WeatherReading> {

    private static final Pattern FILE_NAME = Pattern.compile("weather_\\d{12}\\.json");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final MongoTemplate mongoTemplate;

    public WeatherJsonImporter(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public String dataset() {
        return "WEATHER";
    }

    @Override
    public ImportSource source() {
        return ImportSource.DYNAMIC;
    }

    @Override
    public boolean accepts(String fileName) {
        return FILE_NAME.matcher(fileName).matches();
    }

    @Override
    public ParseResult<WeatherReading> parse(Path file) throws IOException {
        List<Row<WeatherReading>> rows = new ArrayList<>();
        List<ImportError> errors = new ArrayList<>();
        boolean readingsFound = false;
        try (JsonParser parser = JSON.createParser(file.toFile())) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new ImportFormatException("the file must contain a JSON object");
            }
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String name = parser.currentName();
                JsonToken value = parser.nextToken();
                if (!"readings".equals(name)) {
                    parser.skipChildren();
                    continue;
                }
                if (value != JsonToken.START_ARRAY) {
                    throw new ImportFormatException("'readings' must be an array");
                }
                readingsFound = true;
                int position = 0;
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    position++;
                    readReading(JSON.readTree(parser), position, rows, errors);
                }
            }
        } catch (JsonProcessingException e) {
            throw new ImportFormatException("invalid JSON: " + e.getOriginalMessage());
        }
        if (!readingsFound) {
            throw new ImportFormatException("missing 'readings' array");
        }
        return new ParseResult<>(rows, errors);
    }

    /** Type checks: every field must be present and of the right type. */
    private void readReading(JsonNode node, int position, List<Row<WeatherReading>> rows, List<ImportError> errors) {
        if (!node.isObject()) {
            errors.add(new ImportError(position, "reading", "is not a JSON object"));
            return;
        }
        String stationId = text(node, "stationId");
        String delegation = text(node, "delegation");
        String timestampText = text(node, "timestamp");
        Instant timestamp = null;
        if (timestampText != null) {
            try {
                timestamp = Instant.parse(timestampText);
            } catch (DateTimeParseException e) {
                errors.add(new ImportError(position, "timestamp", "'" + timestampText + "' is not an ISO-8601 date"));
                return;
            }
        }
        for (String field : List.of("stationId", "delegation", "timestamp")) {
            if (text(node, field) == null) {
                errors.add(new ImportError(position, field, "is missing"));
                return;
            }
        }
        double[] numbers = new double[4];
        String[] numberFields = {"temperatureC", "humidityPct", "rainfallMm", "windKmh"};
        for (int i = 0; i < numberFields.length; i++) {
            JsonNode value = node.get(numberFields[i]);
            if (value == null || value.isNull()) {
                errors.add(new ImportError(position, numberFields[i], "is missing"));
                return;
            }
            if (!value.isNumber()) {
                errors.add(new ImportError(position, numberFields[i], "'" + value.asText() + "' is not a number"));
                return;
            }
            numbers[i] = value.asDouble();
        }
        rows.add(new Row<>(position, new WeatherReading(null, timestamp,
                new WeatherReading.Meta(stationId.trim(), toDelegationCode(delegation)),
                numbers[0], numbers[1], numbers[2], numbers[3])));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isTextual() || value.asText().isBlank() ? null : value.asText();
    }

    /** "Sakiet Ezzit" in the files becomes SAKIET_EZZIT, the code stored everywhere else. */
    private static String toDelegationCode(String name) {
        return name.trim().toUpperCase().replaceAll("[\\s'-]+", "_");
    }

    @Override
    public Optional<ImportError> validate(Row<WeatherReading> row) {
        WeatherReading reading = row.value();
        int line = row.line();
        if (!isDelegation(reading.meta().delegation())) {
            return Optional.of(new ImportError(line, "delegation", "'" + reading.meta().delegation() + "' is unknown"));
        }
        if (reading.humidityPct() < 0 || reading.humidityPct() > 100) {
            return Optional.of(new ImportError(line, "humidityPct", reading.humidityPct() + " is outside 0-100"));
        }
        if (reading.rainfallMm() < 0) {
            return Optional.of(new ImportError(line, "rainfallMm", reading.rainfallMm() + " is negative"));
        }
        if (reading.windKmh() < 0) {
            return Optional.of(new ImportError(line, "windKmh", reading.windKmh() + " is negative"));
        }
        if (reading.timestamp().isAfter(Instant.now().plus(Duration.ofHours(1)))) {
            return Optional.of(new ImportError(line, "timestamp", "is in the future"));
        }
        return Optional.empty();
    }

    private static boolean isDelegation(String code) {
        try {
            Delegation.valueOf(code);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** A reading is a duplicate when the same station and timestamp appear twice in the file or are already stored. */
    @Override
    public List<ImportError> findDuplicates(List<Row<WeatherReading>> validRows) {
        if (validRows.isEmpty()) {
            return List.of();
        }
        Set<String> stations = new HashSet<>();
        Instant min = validRows.get(0).value().timestamp();
        Instant max = min;
        for (Row<WeatherReading> row : validRows) {
            stations.add(row.value().meta().stationId());
            Instant timestamp = row.value().timestamp();
            min = timestamp.isBefore(min) ? timestamp : min;
            max = timestamp.isAfter(max) ? timestamp : max;
        }
        Set<String> seen = new HashSet<>();
        mongoTemplate.find(Query.query(Criteria.where("meta.stationId").in(stations)
                        .and("timestamp").gte(min).lte(max)), WeatherReading.class)
                .forEach(stored -> seen.add(key(stored)));

        List<ImportError> duplicates = new ArrayList<>();
        for (Row<WeatherReading> row : validRows) {
            if (!seen.add(key(row.value()))) {
                duplicates.add(new ImportError(row.line(), "timestamp", "duplicate reading"));
            }
        }
        return duplicates;
    }

    private static String key(WeatherReading reading) {
        return reading.meta().stationId() + "|" + reading.timestamp();
    }

    /** Time-series collections only accept inserts (no upsert); duplicates were already removed. */
    @Override
    public PersistResult persist(List<WeatherReading> records) {
        int inserted = mongoTemplate.bulkOps(BulkMode.UNORDERED, WeatherReading.class)
                .insert(records).execute().getInsertedCount();
        return new PersistResult(inserted, 0);
    }
}
