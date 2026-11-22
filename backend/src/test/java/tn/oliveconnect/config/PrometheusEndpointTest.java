package tn.oliveconnect.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tn.oliveconnect.AbstractIntegrationTest;

@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "MONGO_URI=mongodb://localhost:27017/oliveconnect?directConnection=true",
        "REDIS_PASSWORD=",
        "JWT_SECRET=production-test-secret-that-is-long-enough",
        "ADMIN_EMAIL=admin@example.tn",
        "ADMIN_PASSWORD=production-admin-secret"
})
class PrometheusEndpointTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void prometheusMetricsAreAvailableWithoutAnApiToken() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")));
    }
}
