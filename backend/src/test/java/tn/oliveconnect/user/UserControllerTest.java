package tn.oliveconnect.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.EnumSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tn.oliveconnect.AbstractIntegrationTest;

class UserControllerTest extends AbstractIntegrationTest {

    static final String ADMIN_EMAIL = "admin@oliveconnect.tn";   // dev defaults of application.yml
    static final String ADMIN_PASSWORD = "admin1234";

    static final String FARMER_JSON = """
            {"email":"%s","password":"secret123","firstName":"Ali","lastName":"Ben Ali","phone":"+21612345678",
             "roles":["FARMER"],
             "farmerProfile":{"memberNumber":"%s","cin":"12345678","address":"Route de Tunis","delegation":"SFAX_VILLE"}}
            """;

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

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")).andReturn();
    }

    private String tokenOf(String email, String password) throws Exception {
        MvcResult result = login(email, password);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String adminToken() throws Exception {
        return "Bearer " + tokenOf(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    private String adminId() {
        return userRepository.findByEmail(ADMIN_EMAIL).orElseThrow().getId();
    }

    private String createFarmer(String email, String memberNumber) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users").header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON).content(FARMER_JSON.formatted(email, memberNumber)))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    @Test
    @DisplayName("T-USR-1 admin creates a Farmer with a profile")
    void adminCreatesFarmerWithProfile() throws Exception {
        mockMvc.perform(post("/api/users").header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON).content(FARMER_JSON.formatted("Ali@Example.TN", "M-001")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.email").value("ali@example.tn"))
                .andExpect(jsonPath("$.roles[0]").value("FARMER"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.farmerProfile.memberNumber").value("M-001"))
                .andExpect(jsonPath("$.farmerProfile.delegation").value("SFAX_VILLE"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());

        // the new Farmer can log in
        assertThat(login("ali@example.tn", "secret123").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("T-USR-2 duplicate email returns 409 EMAIL_TAKEN")
    void duplicateEmailIsRejected() throws Exception {
        createFarmer("ali@example.tn", "M-001");

        mockMvc.perform(post("/api/users").header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON).content(FARMER_JSON.formatted("ALI@example.tn", "M-002")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void duplicateMemberNumberIsRejected() throws Exception {
        createFarmer("ali@example.tn", "M-001");

        mockMvc.perform(post("/api/users").header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON).content(FARMER_JSON.formatted("other@example.tn", "M-001")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_NUMBER_TAKEN"));
    }

    @Test
    @DisplayName("T-USR-3 FARMER without a profile returns 400")
    void farmerWithoutProfileIsRejected() throws Exception {
        mockMvc.perform(post("/api/users").header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@example.tn\",\"password\":\"secret123\",\"firstName\":\"A\","
                        + "\"lastName\":\"B\",\"roles\":[\"FARMER\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("farmerProfile"));
    }

    @Test
    void invalidProfileFieldsAreReportedWithTheirPath() throws Exception {
        mockMvc.perform(post("/api/users").header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@example.tn\",\"password\":\"secret123\",\"firstName\":\"A\","
                        + "\"lastName\":\"B\",\"phone\":\"12\",\"roles\":[\"FARMER\"],"
                        + "\"farmerProfile\":{\"memberNumber\":\"\",\"cin\":\"123\",\"delegation\":\"SFAX_VILLE\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='farmerProfile.memberNumber')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='farmerProfile.cin')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='phone')]").exists());
    }

    @Test
    @DisplayName("T-USR-4 admin disabling themselves returns 422 SELF_LOCKOUT")
    void adminCannotDisableThemselves() throws Exception {
        mockMvc.perform(post("/api/users/" + adminId() + "/disable").header("Authorization", adminToken()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SELF_LOCKOUT"));
    }

    @Test
    void adminCannotRemoveTheirOwnAdminRole() throws Exception {
        mockMvc.perform(put("/api/users/" + adminId()).header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + ADMIN_EMAIL + "\",\"firstName\":\"A\",\"lastName\":\"B\","
                        + "\"roles\":[\"COLLECTION_MANAGER\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SELF_LOCKOUT"));
    }

    @Test
    @DisplayName("T-SEC-4 a Farmer calling the users endpoints gets 403")
    void farmerCannotUseUserEndpoints() throws Exception {
        String farmerId = createFarmer("ali@example.tn", "M-001");
        String farmerToken = "Bearer " + tokenOf("ali@example.tn", "secret123");

        mockMvc.perform(get("/api/users").header("Authorization", farmerToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/users/" + farmerId).header("Authorization", farmerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/users/" + farmerId + "/disable").header("Authorization", farmerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T-AUTH-6 disabling a User revokes their refresh tokens")
    void disablingRevokesRefreshTokens() throws Exception {
        String farmerId = createFarmer("ali@example.tn", "M-001");
        Cookie refresh = login("ali@example.tn", "secret123").getResponse().getCookie("refresh_token");

        mockMvc.perform(post("/api/users/" + farmerId + "/disable").header("Authorization", adminToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
        assertThat(login("ali@example.tn", "secret123").getResponse().getStatus()).isEqualTo(401);

        // and enabling lets them back in
        mockMvc.perform(post("/api/users/" + farmerId + "/enable").header("Authorization", adminToken()))
                .andExpect(status().isNoContent());
        assertThat(login("ali@example.tn", "secret123").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void passwordResetRevokesSessionsAndChangesThePassword() throws Exception {
        String farmerId = createFarmer("ali@example.tn", "M-001");
        Cookie refresh = login("ali@example.tn", "secret123").getResponse().getCookie("refresh_token");

        mockMvc.perform(put("/api/users/" + farmerId).header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(FARMER_JSON.replace("\"password\":\"secret123\"", "\"password\":\"newsecret456\"")
                        .formatted("ali@example.tn", "M-001")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
        assertThat(login("ali@example.tn", "secret123").getResponse().getStatus()).isEqualTo(401);
        assertThat(login("ali@example.tn", "newsecret456").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void emailChangeRevokesSessions() throws Exception {
        String farmerId = createFarmer("ali@example.tn", "M-001");
        Cookie refresh = login("ali@example.tn", "secret123").getResponse().getCookie("refresh_token");

        // password omitted: it stays unchanged
        mockMvc.perform(put("/api/users/" + farmerId).header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new@example.tn\",\"firstName\":\"Ali\",\"lastName\":\"Ben Ali\","
                        + "\"roles\":[\"FARMER\"],\"farmerProfile\":{\"memberNumber\":\"M-001\","
                        + "\"cin\":\"12345678\",\"delegation\":\"SFAX_VILLE\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@example.tn"));

        mockMvc.perform(post("/api/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
        assertThat(login("new@example.tn", "secret123").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void updateCannotTakeAnotherUsersEmail() throws Exception {
        createFarmer("ali@example.tn", "M-001");
        String otherId = createFarmer("other@example.tn", "M-002");

        mockMvc.perform(put("/api/users/" + otherId).header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(FARMER_JSON.formatted("ali@example.tn", "M-002")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void listIsFilteredAndSortedByLastName() throws Exception {
        createFarmer("ali@example.tn", "M-001");
        User manager = new User();
        manager.setEmail("zied@example.tn");
        manager.setPasswordHash(passwordEncoder.encode("secret123"));
        manager.setFirstName("Zied");
        manager.setLastName("Abid");
        manager.setRoles(EnumSet.of(Role.COLLECTION_MANAGER, Role.TEAM_LEADER));
        manager.setEnabled(false);
        userRepository.save(manager);

        // all users: Abid, Ben Ali, OliveConnect
        mockMvc.perform(get("/api/users").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.content[0].lastName").value("Abid"))
                .andExpect(jsonPath("$.content[1].lastName").value("Ben Ali"));

        mockMvc.perform(get("/api/users?role=FARMER").header("Authorization", adminToken()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("ali@example.tn"));

        mockMvc.perform(get("/api/users?enabled=false").header("Authorization", adminToken()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("zied@example.tn"));

        mockMvc.perform(get("/api/users?size=1&page=1").header("Authorization", adminToken()))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].lastName").value("Ben Ali"));
    }

    @Test
    void getUnknownUserReturns404() throws Exception {
        mockMvc.perform(get("/api/users/unknown").header("Authorization", adminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void removingFarmerRoleKeepsTheProfile() throws Exception {
        String farmerId = createFarmer("ali@example.tn", "M-001");

        mockMvc.perform(put("/api/users/" + farmerId).header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ali@example.tn\",\"firstName\":\"Ali\",\"lastName\":\"Ben Ali\","
                        + "\"roles\":[\"LOGISTICS_MANAGER\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("LOGISTICS_MANAGER"))
                .andExpect(jsonPath("$.farmerProfile.memberNumber").value("M-001"));
    }
}
