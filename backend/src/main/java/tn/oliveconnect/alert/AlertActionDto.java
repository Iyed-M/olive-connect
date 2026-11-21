package tn.oliveconnect.alert;

import jakarta.validation.constraints.Size;

public record AlertActionDto(@Size(max = 1000) String resolution) {

    public AlertActionDto {
        resolution = resolution == null || resolution.isBlank() ? null : resolution.trim();
    }
}
