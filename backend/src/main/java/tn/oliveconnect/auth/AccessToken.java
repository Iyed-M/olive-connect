package tn.oliveconnect.auth;

/** A signed JWT and its lifetime in seconds. */
public record AccessToken(String value, long expiresIn) {
}
