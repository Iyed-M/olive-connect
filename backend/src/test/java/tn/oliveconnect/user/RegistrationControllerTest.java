package tn.oliveconnect.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tn.oliveconnect.AbstractIntegrationTest;

class RegistrationControllerTest extends AbstractIntegrationTest {

    static final String ADMIN_EMAIL = "admin@oliveconnect.tn";
    static final String ADMIN_PASSWORD = "admin1234";

    static final String REQUEST_JSON = """
            {"firstName":"Sami","lastName":"Trabelsi","email":"%s","phone":"+21622334455","cin":"12345678",
             "message":"I own 3 ha near Sfax"}
            """;
    static final String APPROVE_JSON = "{\"memberNumber\":\"%s\",\"delegation\":\"SFAX_VILLE\"}";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    RegistrationRequestRepository registrationRequestRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    AdminSeeder adminSeeder;

    @BeforeEach
    void clean() {
        userRepository.deleteAll();
        registrationRequestRepository.deleteAll();
        adminSeeder.seed();
    }

    private String tokenOf(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String adminToken() throws Exception {
        return "Bearer " + tokenOf(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    /** Public submit, returns the id. */
    private String submit(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/registrations").contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST_JSON.formatted(email))).andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private MvcResult approve(String id, String memberNumber) throws Exception {
        return mockMvc.perform(post("/api/registrations/" + id + "/approve").header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON).content(APPROVE_JSON.formatted(memberNumber))).andReturn();
    }

    @Test
    @DisplayName("T-REG-1 a visitor submits a request without logging in")
    void visitorSubmits() throws Exception {
        mockMvc.perform(post("/api/registrations").contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST_JSON.formatted(" Sami@Example.TN ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("sami@example.tn"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    @DisplayName("T-REG-2 invalid request is rejected with field errors")
    void invalidRequest() throws Exception {
        mockMvc.perform(post("/api/registrations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"\",\"lastName\":\"T\",\"email\":\"nope\",\"cin\":\"12\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    @DisplayName("T-REG-3 email of an existing User or of a pending request gives 409 EMAIL_TAKEN")
    void duplicateEmail() throws Exception {
        mockMvc.perform(post("/api/registrations").contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST_JSON.formatted(ADMIN_EMAIL)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));

        submit("sami@example.tn");
        mockMvc.perform(post("/api/registrations").contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST_JSON.formatted("SAMI@example.tn")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    @DisplayName("T-REG-4 admin lists requests filtered by status; others are refused")
    void listByStatus() throws Exception {
        String first = submit("a@example.tn");
        submit("b@example.tn");
        mockMvc.perform(post("/api/registrations/" + first + "/reject").header("Authorization", adminToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(get("/api/registrations").param("status", "PENDING").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("b@example.tn"));
        mockMvc.perform(get("/api/registrations").header("Authorization", adminToken()))
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/registrations")).andExpect(status().isUnauthorized());
        // a Farmer is not an Admin
        String id = submit("farmer@example.tn");
        String password = JsonPath.read(approve(id, "M-100").getResponse().getContentAsString(),
                "$.temporaryPassword");
        String farmerToken = tokenOf("farmer@example.tn", password);
        mockMvc.perform(get("/api/registrations").header("Authorization", "Bearer " + farmerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/registrations/" + id + "/approve").header("Authorization", "Bearer " + farmerToken)
                .contentType(MediaType.APPLICATION_JSON).content(APPROVE_JSON.formatted("M-101")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T-REG-5 approve creates a FARMER with a temporary password that must be changed")
    void approveCreatesFarmer() throws Exception {
        String id = submit("sami@example.tn");

        MvcResult result = approve(id, "M-200");
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = result.getResponse().getContentAsString();
        String temporaryPassword = JsonPath.read(body, "$.temporaryPassword");
        String userId = JsonPath.read(body, "$.userId");
        assertThat(temporaryPassword).hasSize(12);
        assertThat((String) JsonPath.read(body, "$.registration.status")).isEqualTo("APPROVED");

        User user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getRoles()).containsExactly(Role.FARMER);
        assertThat(user.isMustChangePassword()).isTrue();
        assertThat(user.getFarmerProfile().getMemberNumber()).isEqualTo("M-200");
        assertThat(user.getFarmerProfile().getCin()).isEqualTo("12345678");
        assertThat(user.getPasswordHash()).isNotEqualTo(temporaryPassword);

        // the temporary password logs in, and the login response says it must be changed
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"sami@example.tn\",\"password\":\"" + temporaryPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true));
    }

    @Test
    @DisplayName("T-REG-6 changing the password clears mustChangePassword")
    void changePassword() throws Exception {
        String id = submit("sami@example.tn");
        String temporaryPassword = JsonPath.read(approve(id, "M-300").getResponse().getContentAsString(),
                "$.temporaryPassword");
        String token = "Bearer " + tokenOf("sami@example.tn", temporaryPassword);

        mockMvc.perform(patch("/api/me/password").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"wrong-password\",\"newPassword\":\"brandnew123\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));
        mockMvc.perform(patch("/api/me/password").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + temporaryPassword + "\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(patch("/api/me/password").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + temporaryPassword + "\",\"newPassword\":\"brandnew123\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me").header("Authorization", token))
                .andExpect(jsonPath("$.mustChangePassword").value(false));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"sami@example.tn\",\"password\":\"brandnew123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("T-REG-7 a processed request cannot be approved or rejected again (409)")
    void alreadyProcessed() throws Exception {
        String id = submit("sami@example.tn");
        assertThat(approve(id, "M-400").getResponse().getStatus()).isEqualTo(200);

        MvcResult again = approve(id, "M-401");
        assertThat(again.getResponse().getStatus()).isEqualTo(409);
        assertThat((String) JsonPath.read(again.getResponse().getContentAsString(), "$.code"))
                .isEqualTo("REGISTRATION_ALREADY_PROCESSED");
        mockMvc.perform(post("/api/registrations/" + id + "/reject").header("Authorization", adminToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REGISTRATION_ALREADY_PROCESSED"));
        mockMvc.perform(post("/api/registrations/000000000000000000000000/reject")
                .header("Authorization", adminToken())).andExpect(status().isNotFound());
        assertThat(userRepository.count()).isEqualTo(2);   // the seeded Admin + one Farmer
    }

    @Test
    @DisplayName("T-REG-8 approve conflicts: member number taken, email created meanwhile")
    void approveConflicts() throws Exception {
        String first = submit("a@example.tn");
        String second = submit("b@example.tn");
        assertThat(approve(first, "M-500").getResponse().getStatus()).isEqualTo(200);

        MvcResult taken = approve(second, "M-500");
        assertThat(taken.getResponse().getStatus()).isEqualTo(409);
        assertThat((String) JsonPath.read(taken.getResponse().getContentAsString(), "$.code"))
                .isEqualTo("MEMBER_NUMBER_TAKEN");

        // an Admin created the same email by hand after the request was submitted
        String third = submit("c@example.tn");
        mockMvc.perform(post("/api/users").header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(UserControllerTest.FARMER_JSON.formatted("c@example.tn", "M-501")))
                .andExpect(status().isCreated());
        MvcResult emailTaken = approve(third, "M-502");
        assertThat(emailTaken.getResponse().getStatus()).isEqualTo(409);
        assertThat((String) JsonPath.read(emailTaken.getResponse().getContentAsString(), "$.code"))
                .isEqualTo("EMAIL_TAKEN");
        // the failed approvals left both requests pending
        assertThat(registrationRequestRepository.findAll().stream()
                .filter(r -> r.getStatus() == RegistrationStatus.PENDING)).hasSize(2);
    }
}
