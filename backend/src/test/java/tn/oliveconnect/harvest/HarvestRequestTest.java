package tn.oliveconnect.harvest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class HarvestRequestTest {

    @Test
    void transitionAddsAStatusHistoryEntry() {
        HarvestRequest request = new HarvestRequest();

        request.transitionTo(HarvestRequestStatus.SUBMITTED, "farmer-1", null, Instant.parse("2026-11-20T09:00:00Z"));
        request.transitionTo(HarvestRequestStatus.VALIDATED, "manager-1", "Looks good",
                Instant.parse("2026-11-20T10:00:00Z"));

        assertThat(request.getStatus()).isEqualTo(HarvestRequestStatus.VALIDATED);
        assertThat(request.getStatusHistory()).hasSize(2);
        assertThat(request.getStatusHistory().get(0).getFrom()).isNull();
        assertThat(request.getStatusHistory().get(0).getTo()).isEqualTo("SUBMITTED");
        assertThat(request.getStatusHistory().get(1).getFrom()).isEqualTo("SUBMITTED");
        assertThat(request.getStatusHistory().get(1).getByUserId()).isEqualTo("manager-1");
        assertThat(request.getStatusHistory().get(1).getComment()).isEqualTo("Looks good");
    }

    @Test
    void transitionRejectsAnIllegalStateChange() {
        HarvestRequest request = new HarvestRequest();
        request.transitionTo(HarvestRequestStatus.SUBMITTED, "farmer-1", null);

        assertThatThrownBy(() -> request.transitionTo(HarvestRequestStatus.COMPLETED, "manager-1", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUBMITTED")
                .hasMessageContaining("COMPLETED");
    }

    @Test
    void terminalStatesCannotTransition() {
        HarvestRequest request = new HarvestRequest();
        request.transitionTo(HarvestRequestStatus.SUBMITTED, "farmer-1", null);
        request.transitionTo(HarvestRequestStatus.CANCELLED, "farmer-1", "No longer needed");

        assertThat(request.canTransitionTo(HarvestRequestStatus.CANCELLED)).isFalse();
        assertThatThrownBy(() -> request.transitionTo(HarvestRequestStatus.VALIDATED, "manager-1", null))
                .isInstanceOf(IllegalStateException.class);
    }
}
