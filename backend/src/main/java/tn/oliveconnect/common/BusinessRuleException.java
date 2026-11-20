package tn.oliveconnect.common;

/** The request is well formed but a business rule forbids it (SELF_LOCKOUT...). Becomes 422 with the given code. */
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
