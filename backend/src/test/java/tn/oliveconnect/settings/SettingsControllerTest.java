package tn.oliveconnect.settings;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tn.oliveconnect.AbstractIntegrationTest;
import tn.oliveconnect.user.AdminSeeder;
import tn.oliveconnect.user.UserRepository;

class SettingsControllerTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    SystemSettingsRepository repository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    AdminSeeder adminSeeder;

    @BeforeEach
    void clean() {
        repository.deleteAll();
        userRepository.deleteAll();
        adminSeeder.seed();
    }

    @Test
    void settingsHaveDefaultsAndCanBeUpdated() throws Exception {
        String token = adminToken();

        mockMvc.perform(get("/api/settings").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rainAlertMm24h").value(20.0))
                .andExpect(jsonPath("$.defaultBinCapacityKg").value(4000));

        mockMvc.perform(put("/api/settings").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rainAlertMm24h\":35.5,\"defaultBinCapacityKg\":3500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rainAlertMm24h").value(35.5));

        mockMvc.perform(get("/api/settings").header("Authorization", token))
                .andExpect(jsonPath("$.rainAlertMm24h").value(35.5))
                .andExpect(jsonPath("$.defaultBinCapacityKg").value(3500));
        // a single document with _id "global"
        org.assertj.core.api.Assertions.assertThat(repository.findAll()).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(repository.findById("global")).isPresent();
    }

    @Test
    void invalidThresholdIsRejected() throws Exception {
        mockMvc.perform(put("/api/settings").header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rainAlertMm24h\":-5,\"defaultBinCapacityKg\":3500}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("rainAlertMm24h"));
    }

    @Test
    void settingsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/settings")).andExpect(status().isUnauthorized());
    }

    private String adminToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@oliveconnect.tn\",\"password\":\"admin1234\"}"))
                .andExpect(status().isOk()).andReturn();
        return "Bearer " + JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }
}
