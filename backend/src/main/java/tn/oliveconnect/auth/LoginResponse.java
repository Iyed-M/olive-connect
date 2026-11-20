package tn.oliveconnect.auth;

import tn.oliveconnect.user.MeDto;

public record LoginResponse(String accessToken, long expiresIn, MeDto user) {
}
