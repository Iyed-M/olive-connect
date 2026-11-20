package tn.oliveconnect.resource;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record WorkerDto(String id, String firstName, String lastName, String cin, String phone, Set<String> skills,
                        double dailyRate, boolean active, List<UnavailabilityDto> unavailabilities,
                        Instant createdAt, Long version) {

    static WorkerDto from(Worker w) {
        return new WorkerDto(w.getId(), w.getFirstName(), w.getLastName(), w.getCin(), w.getPhone(), w.getSkills(),
                w.getDailyRate(), w.isActive(), w.getUnavailabilities().stream().map(UnavailabilityDto::from).toList(),
                w.getCreatedAt(), w.getVersion());
    }
}
