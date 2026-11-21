package tn.oliveconnect.imports;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tn.oliveconnect.orchard.OliveVariety;
import tn.oliveconnect.orchard.OliveVarietyRepository;
import tn.oliveconnect.orchard.OrchardService;

/** varieties.json: { "varieties": [ { code, name, avgOilYieldPct, optimalMaturityMin, optimalMaturityMax } ] }. */
@Component
public class VarietiesJsonImporter implements DatasetImporter<OliveVariety> {

    // same key as OrchardService: the cached list is stale once a variety changes

    private final OliveVarietyRepository repository;
    private final StringRedisTemplate redis;

    public VarietiesJsonImporter(OliveVarietyRepository repository, StringRedisTemplate redis) {
        this.repository = repository;
        this.redis = redis;
    }

    @Override
    public String dataset() {
        return "VARIETIES";
    }

    @Override
    public ImportSource source() {
        return ImportSource.REFERENCE;
    }

    @Override
    public boolean accepts(String fileName) {
        return fileName.equals("varieties.json");
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public ParseResult<OliveVariety> parse(Path file) throws IOException {
        List<Row<OliveVariety>> rows = new ArrayList<>();
        List<ImportError> errors = new ArrayList<>();
        for (JsonArrayFile.Item item : JsonArrayFile.read(file, "varieties")) {
            FieldReader reader = FieldReader.of(item.position(), item.node());
            OliveVariety variety = new OliveVariety(reader.text("code"), reader.text("name"),
                    reader.decimal("avgOilYieldPct"), reader.decimal("optimalMaturityMin"),
                    reader.decimal("optimalMaturityMax"));
            if (reader.error() != null) {
                errors.add(reader.error());
            } else {
                rows.add(new Row<>(item.position(), variety));
            }
        }
        return new ParseResult<>(rows, errors);
    }

    @Override
    public Optional<ImportError> validate(Row<OliveVariety> row) {
        OliveVariety variety = row.value();
        int line = row.line();
        if (variety.getAvgOilYieldPct() < 0 || variety.getAvgOilYieldPct() > 100) {
            return Optional.of(new ImportError(line, "avgOilYieldPct", variety.getAvgOilYieldPct() + " is outside 0-100"));
        }
        if (variety.getOptimalMaturityMin() < 0 || variety.getOptimalMaturityMax() > 7) {
            return Optional.of(new ImportError(line, "optimalMaturityMin", "the maturity window must stay inside 0-7"));
        }
        if (variety.getOptimalMaturityMin() > variety.getOptimalMaturityMax()) {
            return Optional.of(new ImportError(line, "optimalMaturityMin", "is greater than optimalMaturityMax"));
        }
        return Optional.empty();
    }

    @Override
    public List<ImportError> findDuplicates(List<Row<OliveVariety>> validRows) {
        return RowChecks.duplicateKeys(validRows, OliveVariety::getCode, "code");
    }

    @Override
    public PersistResult persist(List<OliveVariety> records) {
        Map<String, OliveVariety> existing = repository
                .findAllById(records.stream().map(OliveVariety::getCode).toList()).stream()
                .collect(Collectors.toMap(OliveVariety::getCode, Function.identity()));
        int updated = 0;
        List<OliveVariety> toSave = new ArrayList<>();
        for (OliveVariety record : records) {
            OliveVariety variety = existing.get(record.getCode());
            if (variety == null) {
                toSave.add(record);
                continue;
            }
            variety.setName(record.getName());
            variety.setAvgOilYieldPct(record.getAvgOilYieldPct());
            variety.setOptimalMaturityMin(record.getOptimalMaturityMin());
            variety.setOptimalMaturityMax(record.getOptimalMaturityMax());
            toSave.add(variety);
            updated++;
        }
        repository.saveAll(toSave);
        try {
            redis.delete(OrchardService.VARIETY_CACHE_KEY);
        } catch (RuntimeException ignored) {
            // Redis is optional: MongoDB remains the source of truth
        }
        return new PersistResult(toSave.size() - updated, updated);
    }
}
