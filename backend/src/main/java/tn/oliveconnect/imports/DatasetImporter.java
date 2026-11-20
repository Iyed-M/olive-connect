package tn.oliveconnect.imports;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Strategy of one dataset (docs/07 §4). ImportService does the common work (checksum, journal,
 * reject ratio, batches, file moves); an importer only knows how to parse, validate and persist
 * its own file format. Adding a dataset means adding one bean.
 */
public interface DatasetImporter<T> {

    /** Name stored in the journal, e.g. WEATHER. */
    String dataset();

    ImportSource source();

    /** File name pattern of this dataset, e.g. weather_202611210615.json. */
    boolean accepts(String fileName);

    /** Reads the file. Throws ImportFormatException when the file as a whole is unusable. */
    ParseResult<T> parse(Path file) throws IOException;

    /** Range / reference / consistency checks of one parsed record. Empty means valid. */
    Optional<ImportError> validate(Row<T> row);

    /** Checks that need all the valid rows (duplicates inside the file or already stored). Default: none. */
    default List<ImportError> findDuplicates(List<Row<T>> validRows) {
        return List.of();
    }

    /** Writes one batch of valid records (at most 1000). */
    PersistResult persist(List<T> records);

    /** A parsed record and the line (or position) it came from. */
    record Row<T>(int line, T value) {
    }

    /** Parsed records, plus the records that could not even be parsed (wrong number, bad date...). */
    record ParseResult<T>(List<Row<T>> rows, List<ImportError> errors) {
    }

    record PersistResult(int inserted, int updated) {
    }
}
