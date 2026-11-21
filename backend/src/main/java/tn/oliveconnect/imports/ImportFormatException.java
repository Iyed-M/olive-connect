package tn.oliveconnect.imports;

/** The whole file is unusable (missing column, broken JSON...). The job becomes FAILED. */
public class ImportFormatException extends RuntimeException {

    public ImportFormatException(String message) {
        super("FILE_FORMAT: " + message);
    }
}
