package tn.oliveconnect.user;

import java.time.Instant;

public record RegistrationDto(String id, String firstName, String lastName, String email, String phone, String cin,
                              String message, RegistrationStatus status, Instant createdAt, String processedById) {

    static RegistrationDto from(RegistrationRequest r) {
        return new RegistrationDto(r.getId(), r.getFirstName(), r.getLastName(), r.getEmail(), r.getPhone(),
                r.getCin(), r.getMessage(), r.getStatus(), r.getCreatedAt(), r.getProcessedById());
    }
}
