package tn.oliveconnect.auth;

/** What login and refresh produce: the JSON body and the refresh token id that goes in the cookie. */
public record Session(LoginResponse body, String refreshToken) {
}
