package tn.oliveconnect.settings;

import org.springframework.stereotype.Service;

@Service
public class SettingsService {

    private final SystemSettingsRepository repository;

    public SettingsService(SystemSettingsRepository repository) {
        this.repository = repository;
    }

    /** The stored settings, or the defaults while nobody has saved them yet. */
    public SystemSettings get() {
        return repository.findById(SystemSettings.ID).orElseGet(SystemSettings::new);
    }

    public SystemSettings update(SettingsDto dto) {
        SystemSettings settings = get();
        settings.setRainAlertMm24h(dto.rainAlertMm24h());
        settings.setDefaultBinCapacityKg(dto.defaultBinCapacityKg());
        return repository.save(settings);
    }
}
