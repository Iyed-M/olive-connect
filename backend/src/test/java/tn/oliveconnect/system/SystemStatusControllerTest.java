package tn.oliveconnect.system;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tn.oliveconnect.AbstractIntegrationTest;

class SystemStatusControllerTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void statusReportsMongoAndRedisUp() throws Exception {
        mockMvc.perform(get("/api/system/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.api").value("UP"))
                .andExpect(jsonPath("$.mongo").value("UP"))
                .andExpect(jsonPath("$.redis").value("UP"))
                .andExpect(jsonPath("$.version").value("0.0.1-SNAPSHOT"));
    }
}
