package tn.oliveconnect.imports;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Checks shared by the importers. */
final class RowChecks {

    private RowChecks() {
    }

    /** Tunisia bounding box of docs/07 §3. */
    static Optional<ImportError> coordinates(int line, double lng, double lat) {
        if (lng < 7 || lng > 12) {
            return Optional.of(new ImportError(line, "lng", lng + " outside Tunisia"));
        }
        if (lat < 30 || lat > 38) {
            return Optional.of(new ImportError(line, "lat", lat + " outside Tunisia"));
        }
        return Optional.empty();
    }

    /** The second and later rows with the same key are rejected (the first one wins). */
    static <T> List<ImportError> duplicateKeys(List<DatasetImporter.Row<T>> rows, Function<T, String> key,
            String field) {
        Set<String> seen = new HashSet<>();
        List<ImportError> errors = new ArrayList<>();
        for (DatasetImporter.Row<T> row : rows) {
            String value = key.apply(row.value());
            if (!seen.add(value)) {
                errors.add(new ImportError(row.line(), field, "duplicate " + field + " '" + value + "' in the file"));
            }
        }
        return errors;
    }
}
