package tn.oliveconnect.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.EnumSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tn.oliveconnect.AbstractIntegrationTest;
import tn.oliveconnect.user.AdminSeeder;
import tn.oliveconnect.user.Role;
import tn.oliveconnect.user.User;
import tn.oliveconnect.user.UserRepository;

class AuthControllerTest extends AbstractIntegrationTest {

    // same values as the dev defaults in application.yml
    static final String ADMIN_EMAIL = "admin@oliveconnect.tn";
    static final String ADMIN_PASSWORD = "admin1234";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
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

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, password)));
    }

    @Test
    void validAdminLoginReturnsTokenAndUser() throws Exception {   // T-AUTH-1
        login(ADMIN_EMAIL, ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.user.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void emailIsTrimmedAndCaseInsensitive() throws Exception {
        login("  Admin@OliveConnect.TN ", ADMIN_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void disabledUserCannotLogIn() throws Exception {   // T-AUTH-3
        User user = new User();
        user.setEmail("farmer@example.tn");
        user.setPasswordHash(passwordEncoder.encode("secret123"));
        user.setFirstName("Ali");
        user.setLastName("Ben Ali");
        user.setRoles(EnumSet.of(Role.FARMER));
        user.setEnabled(false);
        userRepository.save(user);

        login("farmer@example.tn", "secret123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameError() throws Exception {
        login(ADMIN_EMAIL, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        login("nobody@example.tn", "whatever1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void blankEmailIsRejectedWithFieldErrors() throws Exception {
        login("", ADMIN_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Test
    void meWithoutTokenIsUnauthenticated() throws Exception {   // T-SEC-5
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void meWithGarbageTokenIsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void meWithTokenReturnsTheUser() throws Exception {
        String body = login(ADMIN_EMAIL, ADMIN_PASSWORD).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.firstName").value("Admin"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.onboardingDone").value(false));
    }
}
