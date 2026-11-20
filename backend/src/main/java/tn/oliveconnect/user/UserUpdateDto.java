package tn.oliveconnect.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** Same as the create DTO, but the password is optional: when present it resets the password. */
public record UserUpdateDto(
        @NotBlank @Email String email,
        @Size(min = 8, max = 72) String password,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @Pattern(regexp = "\\+216\\d{8}", message = "must be +216 followed by 8 digits") String phone,
        @NotEmpty Set<Role> roles,
        @Valid FarmerProfileDto farmerProfile) {
}
