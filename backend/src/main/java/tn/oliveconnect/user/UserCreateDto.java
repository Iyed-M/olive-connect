package tn.oliveconnect.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import java.util.Set;

public record UserCreateDto(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @Pattern(regexp = "\\+216\\d{8}", message = "must be +216 followed by 8 digits") String phone,
        @NotEmpty Set<Role> roles,
        @Valid FarmerProfileDto farmerProfile) {

    /** Normalizes before validation: trimmed lowercase email (decision 14), blank phone means no phone (decision 21). */
    public UserCreateDto {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        phone = phone == null || phone.isBlank() ? null : phone.trim();
    }
}
