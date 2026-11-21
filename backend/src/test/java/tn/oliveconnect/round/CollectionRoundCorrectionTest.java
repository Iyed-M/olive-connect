package tn.oliveconnect.round;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
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
import tn.oliveconnect.user.Role;
import tn.oliveconnect.user.User;
import tn.oliveconnect.user.UserRepository;

class CollectionRoundCorrectionTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired CollectionRoundRepository roundRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void clean() {
        roundRepository.deleteAll();
    }

    private String tokenFor(Role role) throws Exception {
        String email = role.name().toLowerCase() + "@round-correction.tn";
        if (userRepository.findByEmail(email).isEmpty()) {
            User user = new User();
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode("secret123"));
            user.setFirstName("Test");
            user.setLastName(role.name());
            user.setRoles(EnumSet.of(role));
            userRepository.save(user);
        }
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"secret123\"}")).andReturn();
        return "Bearer " + JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private CollectionRound saveRound(RoundStatus status) {
        CollectionRound round = new CollectionRound();
        round.setCode("R-" + status);
        round.setDate(LocalDate.of(2026, 11, 20));
        round.setStatus(status);
        round.setTotalHarvestedKg(1000);
        return roundRepository.save(round);
    }

    @Test
    @DisplayName("FR-6.5 admin corrects the weight of a completed round and the correction is recorded")
    void adminCorrectsCompletedRound() throws Exception {
        CollectionRound round = saveRound(RoundStatus.COMPLETED);

        mockMvc.perform(patch("/api/rounds/" + round.getId() + "/correction")
                        .header("Authorization", tokenFor(Role.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalHarvestedKg\":1250.5,\"comment\":\"Mill scale reading\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHarvestedKg").value(1250.5))
                .andExpect(jsonPath("$.corrections.length()").value(1))
                .andExpect(jsonPath("$.corrections[0].comment").value("Mill scale reading"));
    }

    @Test
    @DisplayName("FR-6.5 errors: round not completed (422), missing comment (400), logistics (403), unknown (404)")
    void errors() throws Exception {
        CollectionRound planned = saveRound(RoundStatus.PLANNED);
        CollectionRound completed = saveRound(RoundStatus.COMPLETED);
        String admin = tokenFor(Role.ADMIN);
        String body = "{\"totalHarvestedKg\":10,\"comment\":\"x\"}";

        mockMvc.perform(patch("/api/rounds/" + planned.getId() + "/correction").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ROUND_NOT_COMPLETED"));
        mockMvc.perform(patch("/api/rounds/" + completed.getId() + "/correction").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"totalHarvestedKg\":10}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/rounds/" + completed.getId() + "/correction")
                        .header("Authorization", tokenFor(Role.LOGISTICS_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/rounds/nope/correction").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }
}
