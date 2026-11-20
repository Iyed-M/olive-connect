package tn.oliveconnect.common;

/** The request clashes with existing data (duplicate email...). Becomes 409 with the given code. */
public class ConflictException extends RuntimeException {

    private final String code;

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
