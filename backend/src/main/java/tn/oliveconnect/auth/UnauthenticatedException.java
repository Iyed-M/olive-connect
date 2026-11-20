package tn.oliveconnect.auth;

/** The token is valid but its User no longer exists: the caller must log in again (401). */
public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException(String message) {
        super(message);
    }
}
