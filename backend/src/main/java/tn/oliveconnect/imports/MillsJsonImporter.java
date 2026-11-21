package tn.oliveconnect.imports;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tn.oliveconnect.mill.Mill;
import tn.oliveconnect.mill.MillRepository;
import tn.oliveconnect.orchard.GeoPoint;

/** mills.json: { "mills": [ { code, name, lng, lat, dailyCapacityKg } ] }, upsert on code. */
@Component
public class MillsJsonImporter implements DatasetImporter<Mill> {

    private final MillRepository repository;

    public MillsJsonImporter(MillRepository repository) {
        this.repository = repository;
    }

    @Override
    public String dataset() {
        return "MILLS";
    }

    @Override
    public ImportSource source() {
        return ImportSource.REFERENCE;
    }

    @Override
    public boolean accepts(String fileName) {
        return fileName.equals("mills.json");
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public ParseResult<Mill> parse(Path file) throws IOException {
        List<Row<Mill>> rows = new ArrayList<>();
        List<ImportError> errors = new ArrayList<>();
        for (JsonArrayFile.Item item : JsonArrayFile.read(file, "mills")) {
            FieldReader reader = FieldReader.of(item.position(), item.node());
            Mill mill = new Mill();
            mill.setCode(reader.text("code"));
            mill.setName(reader.text("name"));
            mill.setLocation(new GeoPoint(reader.decimal("lng"), reader.decimal("lat")));
            mill.setDailyCapacityKg(reader.decimal("dailyCapacityKg"));
            if (reader.error() != null) {
                errors.add(reader.error());
            } else {
                rows.add(new Row<>(item.position(), mill));
            }
        }
        return new ParseResult<>(rows, errors);
    }

    @Override
    public Optional<ImportError> validate(Row<Mill> row) {
        Mill mill = row.value();
        Optional<ImportError> outside = RowChecks.coordinates(row.line(), mill.getLocation().longitude(),
                mill.getLocation().latitude());
        if (outside.isPresent()) {
            return outside;
        }
        if (mill.getDailyCapacityKg() < 0) {
            return Optional.of(new ImportError(row.line(), "dailyCapacityKg", mill.getDailyCapacityKg() + " is negative"));
        }
        return Optional.empty();
    }

    @Override
    public List<ImportError> findDuplicates(List<Row<Mill>> validRows) {
        return RowChecks.duplicateKeys(validRows, Mill::getCode, "code");
    }

    /** A new mill starts empty (all its capacity available); an existing one keeps its availability. */
    @Override
    public PersistResult persist(List<Mill> records) {
        Map<String, Mill> existing = repository.findAllByCodeIn(records.stream().map(Mill::getCode).toList()).stream()
                .collect(Collectors.toMap(Mill::getCode, Function.identity()));
        int updated = 0;
        List<Mill> toSave = new ArrayList<>();
        for (Mill record : records) {
            Mill mill = existing.get(record.getCode());
            if (mill == null) {
                record.setAvailableCapacityKg(record.getDailyCapacityKg());
                toSave.add(record);
                continue;
            }
            mill.setName(record.getName());
            mill.setLocation(record.getLocation());
            mill.setDailyCapacityKg(record.getDailyCapacityKg());
            mill.setAvailableCapacityKg(Math.min(mill.getAvailableCapacityKg(), record.getDailyCapacityKg()));
            toSave.add(mill);
            updated++;
        }
        repository.saveAll(toSave);
        return new PersistResult(toSave.size() - updated, updated);
    }
}
