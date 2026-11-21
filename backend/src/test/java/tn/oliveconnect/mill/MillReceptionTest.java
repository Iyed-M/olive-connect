package tn.oliveconnect.mill;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MillReceptionTest {

    @Test
    void computesNetWeightAndFillRateFromTheStoredWeights() {
        MillReception reception = new MillReception();
        reception.setGrossWeightKg(4350);
        reception.setTareKg(820);
        reception.setBinCapacityKg(4000);

        reception.computeNetAndFillRate();

        assertThat(reception.getNetWeightKg()).isEqualTo(3530);
        assertThat(reception.getFillRate()).isEqualTo(0.8825);
    }
}
