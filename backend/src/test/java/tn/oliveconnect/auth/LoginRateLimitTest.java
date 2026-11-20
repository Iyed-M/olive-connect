package tn.oliveconnect.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tn.oliveconnect.AbstractIntegrationTest;
import tn.oliveconnect.user.AdminSeeder;
import tn.oliveconnect.user.UserRepository;

class LoginRateLimitTest extends AbstractIntegrationTest {

    // same values as the dev defaults in application.yml
    static final String ADMIN_EMAIL = "admin@oliveconnect.tn";
    static final String ADMIN_PASSWORD = "admin1234";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    AdminSeeder adminSeeder;

    @BeforeEach
    void cleanUsers() {
        userRepository.deleteAll();
        adminSeeder.seed();
    }

    private String loginBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, password)));
    }

    @Test
    void sixthAttemptAfterFiveFailuresIsRejected() throws Exception {   // T-AUTH-2
        for (int i = 0; i < 5; i++) {
            login(ADMIN_EMAIL, "wrong-password").andExpect(status().isUnauthorized());
        }
        login(ADMIN_EMAIL, "wrong-password")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
    }

    @Test
    void blockedEvenWithTheRightPassword() throws Exception {   // T-AUTH-12
        for (int i = 0; i < 5; i++) {
            login(ADMIN_EMAIL, "wrong-password").andExpect(status().isUnauthorized());
        }
        login(ADMIN_EMAIL, ADMIN_PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void successResetsTheCounter() throws Exception {   // T-AUTH-13
        for (int i = 0; i < 4; i++) {
            login(ADMIN_EMAIL, "wrong-password").andExpect(status().isUnauthorized());
        }
        login(ADMIN_EMAIL, ADMIN_PASSWORD).andExpect(status().isOk());
        for (int i = 0; i < 5; i++) {
            login(ADMIN_EMAIL, "wrong-password").andExpect(status().isUnauthorized());
        }
    }
}
