package tn.oliveconnect.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Secrets and seed values read from the environment (see application.yml).
 * Validation runs at startup: a missing or too short value stops the API.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        // HS256 needs a key of at least 32 bytes (32 characters when ASCII)
        @NotBlank @Size(min = 32, message = "JWT_SECRET must be at least 32 characters") String jwtSecret,
        @NotBlank String adminEmail,
        @NotBlank String adminPassword,
        @NotNull @Valid Imports imports) {

    /** Data import settings: the inbox folder has processed/ and rejected/ siblings inside it. */
    public record Imports(@NotBlank String inboxDir) {
    }
}
