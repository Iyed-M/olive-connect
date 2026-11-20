package tn.oliveconnect.auth;

/** Unknown email, wrong password or disabled User: always the same error, so nothing leaks. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
