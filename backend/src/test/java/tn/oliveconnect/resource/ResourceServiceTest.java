package tn.oliveconnect.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import tn.oliveconnect.common.FieldValidationException;

/** Pure validation rules, no Spring and no database (the repositories are not used by these methods). */
class ResourceServiceTest {

    private final ResourceService service = new ResourceService(null, null, null);
    private final Instant from = Instant.parse("2026-11-25T00:00:00Z");

    @Test
    void unavailabilityEndMustBeAfterStart() {
        assertThatThrownBy(() -> service.toUnavailability(new UnavailabilityDto(from, from, null)))
                .isInstanceOfSatisfying(FieldValidationException.class, e -> assertThat(e.getField()).isEqualTo("to"));
        assertThatThrownBy(() -> service.toUnavailability(new UnavailabilityDto(from, from.minusSeconds(1), null)))
                .isInstanceOf(FieldValidationException.class);
        assertThat(service.toUnavailability(new UnavailabilityDto(from, from.plusSeconds(1), "Leave")).getReason())
                .isEqualTo("Leave");
    }

    @Test
    void requiredRejectsNullAndBlank() {
        assertThatThrownBy(() -> service.required("powerHp", null)).isInstanceOf(FieldValidationException.class);
        assertThatThrownBy(() -> service.required("plateNumber", "  ")).isInstanceOf(FieldValidationException.class);
        assertThat(service.required("powerHp", 90)).isEqualTo(90);
    }
}
