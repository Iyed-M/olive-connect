package tn.oliveconnect.orchard;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record OrchardWriteDto(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 40) String delegation,
        @NotNull @Valid GeoPointDto location,
        @NotEmpty @Size(max = 20) List<@NotNull @Valid PlotDto> plots,
        String ownerId) {
}
