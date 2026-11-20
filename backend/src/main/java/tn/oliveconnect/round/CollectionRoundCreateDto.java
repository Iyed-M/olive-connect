package tn.oliveconnect.round;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record CollectionRoundCreateDto(
        @NotNull LocalDate date,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotBlank String teamLeaderId,
        @NotEmpty @Size(max = 15) @JsonAlias({"harvestRequestIds", "stopRequestIds"}) List<String> requestIds,
        List<String> workerIds,
        List<String> equipmentIds,
        @NotBlank String millId) {

    public CollectionRoundCreateDto {
        requestIds = requestIds == null ? List.of() : List.copyOf(requestIds);
        workerIds = workerIds == null ? List.of() : List.copyOf(workerIds);
        equipmentIds = equipmentIds == null ? List.of() : List.copyOf(equipmentIds);
    }
}
