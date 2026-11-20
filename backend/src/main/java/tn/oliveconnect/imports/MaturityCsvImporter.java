package tn.oliveconnect.imports;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import tn.oliveconnect.orchard.Orchard;
import tn.oliveconnect.orchard.OrchardRepository;

/**
 * maturity_YYYYMMDDHHmm.csv (docs/07 §2): orchardExternalRef;timestamp;maturityIndex;source. Readings go to the
 * time-series collection maturity_readings; orchards.currentMaturityIndex follows the latest reading (R11).
 */
@Component
public class MaturityCsvImporter implements DatasetImporter<MaturityCsvImporter.MaturityLine> {

    private static final List<String> COLUMNS = List.of("orchardExternalRef", "timestamp", "maturityIndex", "source");
    private static final List<String> SOURCES = List.of("SENSOR", "MANUAL");

    public record MaturityLine(String orchardExternalRef, Instant timestamp, double maturityIndex, String source) {
    }

    private final OrchardRepository orchardRepository;
    private final MongoTemplate mongoTemplate;

    public MaturityCsvImporter(OrchardRepository orchardRepository, MongoTemplate mongoTemplate) {
        this.orchardRepository = orchardRepository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public String dataset() {
        return "MATURITY";
    }

    @Override
    public ImportSource source() {
        return ImportSource.DYNAMIC;
    }

    @Override
    public boolean accepts(String fileName) {
        return fileName.matches("maturity_\\d{12}\\.csv");
    }

    @Override
    public ParseResult<MaturityLine> parse(Path file) throws IOException {
        CsvFile.Content csv = CsvFile.read(file, COLUMNS);
        List<ImportError> errors = new ArrayList<>(csv.errors());
        List<Row<MaturityLine>> rows = new ArrayList<>();
        for (CsvFile.Line line : csv.lines()) {
            FieldReader reader = new FieldReader(line.number(), line.values()::get);
            MaturityLine reading = new MaturityLine(reader.text("orchardExternalRef"), reader.instant("timestamp"),
                    reader.decimal("maturityIndex"), reader.text("source"));
            if (reader.error() != null) {
                errors.add(reader.error());
            } else {
                rows.add(new Row<>(line.number(), reading));
            }
        }
        return new ParseResult<>(rows, errors);
    }

    @Override
    public Optional<ImportError> validate(Row<MaturityLine> row) {
        MaturityLine reading = row.value();
        if (reading.maturityIndex() < 0 || reading.maturityIndex() > 7) {
            return Optional.of(new ImportError(row.line(), "maturityIndex", reading.maturityIndex() + " is outside 0-7"));
        }
        if (!SOURCES.contains(reading.source())) {
            return Optional.of(new ImportError(row.line(), "source", "'" + reading.source() + "' is not SENSOR or MANUAL"));
        }
        if (reading.timestamp().isAfter(Instant.now().plus(Duration.ofHours(1)))) {
            return Optional.of(new ImportError(row.line(), "timestamp", "is in the future"));
        }
        return Optional.empty();
    }

    /** The orchard must exist (one query for the file), and a reading (orchard, timestamp) is stored once. */
    @Override
    public List<ImportError> findDuplicates(List<Row<MaturityLine>> validRows) {
        if (validRows.isEmpty()) {
            return List.of();
        }
        Set<String> refs = validRows.stream().map(row -> row.value().orchardExternalRef()).collect(Collectors.toSet());
        Map<String, Orchard> orchards = orchardsByRef(refs);

        Instant min = validRows.get(0).value().timestamp();
        Instant max = min;
        for (Row<MaturityLine> row : validRows) {
            Instant timestamp = row.value().timestamp();
            min = timestamp.isBefore(min) ? timestamp : min;
            max = timestamp.isAfter(max) ? timestamp : max;
        }
        Set<String> seen = new HashSet<>();
        mongoTemplate.find(Query.query(Criteria.where("meta.orchardId")
                        .in(orchards.values().stream().map(Orchard::getId).toList())
                        .and("timestamp").gte(min).lte(max)), MaturityReading.class)
                .forEach(stored -> seen.add(stored.meta().orchardId() + "|" + stored.timestamp()));

        List<ImportError> errors = new ArrayList<>();
        for (Row<MaturityLine> row : validRows) {
            MaturityLine reading = row.value();
            Orchard orchard = orchards.get(reading.orchardExternalRef());
            if (orchard == null) {
                errors.add(new ImportError(row.line(), "orchardExternalRef",
                        "orchardExternalRef '" + reading.orchardExternalRef() + "' unknown"));
            } else if (!seen.add(orchard.getId() + "|" + reading.timestamp())) {
                errors.add(new ImportError(row.line(), "timestamp", "duplicate reading"));
            }
        }
        return errors;
    }

    @Override
    public PersistResult persist(List<MaturityLine> records) {
        Map<String, Orchard> orchards = orchardsByRef(
                records.stream().map(MaturityLine::orchardExternalRef).collect(Collectors.toSet()));
        List<MaturityReading> readings = new ArrayList<>();
        Map<String, MaturityLine> latest = new HashMap<>();   // newest reading of each orchard in this batch
        for (MaturityLine record : records) {
            Orchard orchard = orchards.get(record.orchardExternalRef());
            readings.add(new MaturityReading(null, record.timestamp(),
                    new MaturityReading.Meta(orchard.getId(), record.source()), record.maturityIndex(),
                    orchard.getMainVarietyCode()));
            latest.merge(orchard.getId(), record, (a, b) -> b.timestamp().isAfter(a.timestamp()) ? b : a);
        }
        int inserted = mongoTemplate.bulkOps(BulkMode.UNORDERED, MaturityReading.class)
                .insert(readings).execute().getInsertedCount();

        // only a reading newer than the one already on the orchard replaces it
        BulkOperations updates = mongoTemplate.bulkOps(BulkMode.UNORDERED, Orchard.class);
        latest.forEach((orchardId, reading) -> updates.updateOne(
                Query.query(Criteria.where("_id").is(orchardId).orOperator(
                        Criteria.where("maturityUpdatedAt").is(null),
                        Criteria.where("maturityUpdatedAt").lt(reading.timestamp()))),
                new Update().set("currentMaturityIndex", reading.maturityIndex())
                        .set("maturityUpdatedAt", reading.timestamp())));
        updates.execute();
        return new PersistResult(inserted, 0);
    }

    private Map<String, Orchard> orchardsByRef(Set<String> refs) {
        return orchardRepository.findAllByExternalRefIn(refs).stream()
                .collect(Collectors.toMap(Orchard::getExternalRef, Function.identity()));
    }
}
