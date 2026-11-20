package tn.oliveconnect.round;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record CollectionRoundUpdateDto(
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        String teamLeaderId,
        @JsonAlias({"harvestRequestIds", "stopRequestIds"}) List<String> requestIds,
        List<String> workerIds,
        List<String> equipmentIds,
        String millId,
        Long version) {
}
