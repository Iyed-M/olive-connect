package tn.oliveconnect.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/** Body of the public POST /api/registrations. */
public record RegistrationCreateDto(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank @Email String email,
        @Pattern(regexp = "\\+216\\d{8}", message = "must be +216 followed by 8 digits") String phone,
        @NotBlank @Pattern(regexp = "\\d{8}", message = "must be exactly 8 digits") String cin,
        @Size(max = 1000) String message) {

    /** Same normalization as UserCreateDto: trimmed lowercase email, blank phone means no phone. */
    public RegistrationCreateDto {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        phone = phone == null || phone.isBlank() ? null : phone.trim();
    }
}
