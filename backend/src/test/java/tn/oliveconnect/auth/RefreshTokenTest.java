package tn.oliveconnect.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.EnumSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tn.oliveconnect.AbstractIntegrationTest;
import tn.oliveconnect.user.AdminSeeder;
import tn.oliveconnect.user.Role;
import tn.oliveconnect.user.User;
import tn.oliveconnect.user.UserRepository;

class RefreshTokenTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    AdminSeeder adminSeeder;
    @Autowired
    StringRedisTemplate redis;

    @BeforeEach
    void cleanUsers() {
        userRepository.deleteAll();
        adminSeeder.seed();
    }

    private MvcResult login() throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@oliveconnect.tn\",\"password\":\"admin1234\"}"))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String refreshTokenOf(MvcResult result) {
        return result.getResponse().getCookie("refresh_token").getValue();
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String token) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", token)));
    }

    @Test
    void loginSetsTheRefreshCookie() throws Exception {
        String setCookie = login().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie).startsWith("refresh_token=")
                .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth", "Max-Age=604800");
    }

    @Test
    void refreshRotatesTheCookieAndTheOldOneStopsWorking() throws Exception {   // T-AUTH-4
        String first = refreshTokenOf(login());

        MvcResult result = refresh(first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value("admin@oliveconnect.tn"))
                .andReturn();
        String second = refreshTokenOf(result);
        assertThat(second).isNotEqualTo(first);

        refresh(first).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        refresh(second).andExpect(status().isOk());
    }

    @Test
    void logoutEndsTheSessionAndIsIdempotent() throws Exception {   // T-AUTH-5
        String token = refreshTokenOf(login());

        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie("refresh_token", token)))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh_token", 0));
        refresh(token).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie("refresh_token", token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
    }

    @Test
    void refreshWithoutCookieIsUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void refreshForADisabledUserRevokesAllTheirSessions() throws Exception {
        MvcResult first = login();
        login();   // a second session, on another device
        String userId = JsonPath.read(first.getResponse().getContentAsString(), "$.user.id");
        assertThat(redis.opsForSet().size("user:" + userId + ":refresh")).isEqualTo(2);

        User user = userRepository.findById(userId).orElseThrow();
        user.setEnabled(false);
        userRepository.save(user);

        refresh(refreshTokenOf(first)).andExpect(status().isUnauthorized());
        assertThat(redis.hasKey("user:" + userId + ":refresh")).isFalse();
    }

    @Test
    void refreshCarriesTheCurrentRoles() throws Exception {
        MvcResult login = login();
        String userId = JsonPath.read(login.getResponse().getContentAsString(), "$.user.id");
        User user = userRepository.findById(userId).orElseThrow();
        user.setRoles(EnumSet.of(Role.ADMIN, Role.FARMER));
        userRepository.save(user);

        MvcResult result = refresh(refreshTokenOf(login)).andExpect(status().isOk()).andReturn();
        assertThat((java.util.List<String>) JsonPath.read(result.getResponse().getContentAsString(), "$.user.roles"))
                .containsExactlyInAnyOrder("ADMIN", "FARMER");
    }
}
