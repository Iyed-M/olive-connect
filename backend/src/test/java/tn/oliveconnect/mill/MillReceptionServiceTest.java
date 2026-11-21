package tn.oliveconnect.mill;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tn.oliveconnect.common.BusinessRuleException;

class MillReceptionServiceTest {

    private final MillReceptionService service = new MillReceptionService(null, null, null, null, null, null, null, null);

    @Test
    void rejectsNonPositiveOrOverCapacityWeights() {
        assertThatThrownBy(() -> service.validateWeights(0, 100, 4000))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.validateWeights(100, 100, 4000))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.validateWeights(6000, 100, 4000))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void allowsAValidWeighingWithinTheBinTolerance() {
        service.validateWeights(5500, 900, 4000);
    }
}
