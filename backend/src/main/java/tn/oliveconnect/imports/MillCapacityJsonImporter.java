package tn.oliveconnect.imports;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tn.oliveconnect.mill.Mill;
import tn.oliveconnect.mill.MillRepository;

/** mill_capacity_YYYYMMDD.json: { "date": ..., "mills": [ { millCode, dailyCapacityKg, availableCapacityKg } ] }. */
@Component
public class MillCapacityJsonImporter implements DatasetImporter<MillCapacityJsonImporter.MillCapacity> {

    public record MillCapacity(String millCode, double dailyCapacityKg, double availableCapacityKg, Instant date) {
    }

    private final MillRepository repository;

    public MillCapacityJsonImporter(MillRepository repository) {
        this.repository = repository;
    }

    @Override
    public String dataset() {
        return "MILL_CAPACITY";
    }

    @Override
    public ImportSource source() {
        return ImportSource.DYNAMIC;
    }

    @Override
    public boolean accepts(String fileName) {
        return fileName.matches("mill_capacity_\\d{8}\\.json");
    }

    @Override
    public ParseResult<MillCapacity> parse(Path file) throws IOException {
        List<Row<MillCapacity>> rows = new ArrayList<>();
        List<ImportError> errors = new ArrayList<>();
        Instant date = fileDate(file);
        for (JsonArrayFile.Item item : JsonArrayFile.read(file, "mills")) {
            FieldReader reader = FieldReader.of(item.position(), item.node());
            MillCapacity capacity = new MillCapacity(reader.text("millCode"), reader.decimal("dailyCapacityKg"),
                    reader.decimal("availableCapacityKg"), date);
            if (reader.error() != null) {
                errors.add(reader.error());
            } else {
                rows.add(new Row<>(item.position(), capacity));
            }
        }
        return new ParseResult<>(rows, errors);
    }

    /** The date of the file comes from its name (mill_capacity_YYYYMMDD.json). */
    private static Instant fileDate(Path file) {
        String name = file.getFileName().toString();
        String digits = name.substring("mill_capacity_".length(), "mill_capacity_".length() + 8);
        return LocalDate.parse(digits, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    @Override
    public Optional<ImportError> validate(Row<MillCapacity> row) {
        MillCapacity capacity = row.value();
        if (capacity.dailyCapacityKg() < 0) {
            return Optional.of(new ImportError(row.line(), "dailyCapacityKg", capacity.dailyCapacityKg() + " is negative"));
        }
        if (capacity.availableCapacityKg() < 0) {
            return Optional.of(new ImportError(row.line(), "availableCapacityKg", capacity.availableCapacityKg() + " is negative"));
        }
        if (capacity.availableCapacityKg() > capacity.dailyCapacityKg()) {
            return Optional.of(new ImportError(row.line(), "availableCapacityKg", "is greater than dailyCapacityKg"));
        }
        return Optional.empty();
    }

    /** Reference check (the mill must exist) and duplicate mill codes in the file. */
    @Override
    public List<ImportError> findDuplicates(List<Row<MillCapacity>> validRows) {
        Set<String> known = repository.findAllByCodeIn(validRows.stream().map(row -> row.value().millCode()).toList())
                .stream().map(Mill::getCode).collect(Collectors.toSet());
        Set<String> seen = new HashSet<>();
        List<ImportError> errors = new ArrayList<>();
        for (Row<MillCapacity> row : validRows) {
            String code = row.value().millCode();
            if (!known.contains(code)) {
                errors.add(new ImportError(row.line(), "millCode", "'" + code + "' unknown"));
            } else if (!seen.add(code)) {
                errors.add(new ImportError(row.line(), "millCode", "duplicate millCode '" + code + "' in the file"));
            }
        }
        return errors;
    }

    @Override
    public PersistResult persist(List<MillCapacity> records) {
        Map<String, Mill> mills = repository.findAllByCodeIn(records.stream().map(MillCapacity::millCode).toList())
                .stream().collect(Collectors.toMap(Mill::getCode, Function.identity()));
        List<Mill> toSave = new ArrayList<>();
        for (MillCapacity record : records) {
            Mill mill = mills.get(record.millCode());
            // a file older than the one already applied must not overwrite newer data
            if (mill.getCapacityUpdatedAt() != null && mill.getCapacityUpdatedAt().isAfter(record.date())) {
                continue;
            }
            mill.setAvailableCapacityKg(record.availableCapacityKg());
            mill.setCapacityUpdatedAt(record.date());
            toSave.add(mill);
        }
        repository.saveAll(toSave);
        return new PersistResult(0, toSave.size());
    }
}
