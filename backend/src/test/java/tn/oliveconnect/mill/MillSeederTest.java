package tn.oliveconnect.mill;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tn.oliveconnect.AbstractIntegrationTest;

class MillSeederTest extends AbstractIntegrationTest {

    @Autowired MillRepository millRepository;
    @Autowired MillSeeder millSeeder;

    @BeforeEach
    void clean() {
        millRepository.deleteAll();
    }

    @Test
    void seedingTwiceKeepsOneFixedReferenceSet() {
        millSeeder.seed();
        millSeeder.seed();

        assertThat(millRepository.findAll()).hasSize(3);
        assertThat(millRepository.findByName("Sfax North Olive Mill")).isPresent();
    }
}
