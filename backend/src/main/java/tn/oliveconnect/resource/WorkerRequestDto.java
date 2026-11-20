package tn.oliveconnect.resource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Set;

/** Body of POST and PUT /workers. "active" defaults to true when omitted. */
public record WorkerRequestDto(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank @Pattern(regexp = "\\d{8}", message = "must be exactly 8 digits") String cin,
        @Pattern(regexp = "\\+216\\d{8}", message = "must be +216 followed by 8 digits") String phone,
        Set<@NotBlank String> skills,
        @NotNull @PositiveOrZero Double dailyRate,
        Boolean active) {

    /** Blank phone means no phone, like the Users. */
    public WorkerRequestDto {
        phone = phone == null || phone.isBlank() ? null : phone.trim();
    }
}
