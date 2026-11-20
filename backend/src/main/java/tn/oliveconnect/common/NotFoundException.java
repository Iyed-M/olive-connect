package tn.oliveconnect.common;

/** The resource does not exist (or must look like it does not exist). Becomes 404 NOT_FOUND. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
