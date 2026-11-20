package tn.oliveconnect.settings;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SettingsDto(
        @NotNull @Positive Double rainAlertMm24h,
        @NotNull @Positive Integer defaultBinCapacityKg) {

    static SettingsDto from(SystemSettings settings) {
        return new SettingsDto(settings.getRainAlertMm24h(), settings.getDefaultBinCapacityKg());
    }
}
