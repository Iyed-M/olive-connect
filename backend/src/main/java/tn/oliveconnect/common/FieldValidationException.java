package tn.oliveconnect.common;

/** A rule Bean Validation cannot express on one field (e.g. "profile required when FARMER"). Becomes 400 VALIDATION_FAILED. */
public class FieldValidationException extends RuntimeException {

    private final String field;

    public FieldValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
