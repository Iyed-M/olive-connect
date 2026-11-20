package tn.oliveconnect.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordDto(@NotBlank String currentPassword,
                                @NotBlank @Size(min = 8, max = 72) String newPassword) {
}
