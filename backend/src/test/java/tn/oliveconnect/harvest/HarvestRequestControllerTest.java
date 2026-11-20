package tn.oliveconnect.harvest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tn.oliveconnect.AbstractIntegrationTest;
import tn.oliveconnect.notification.NotificationRepository;
import tn.oliveconnect.orchard.GeoPoint;
import tn.oliveconnect.orchard.Orchard;
import tn.oliveconnect.orchard.OrchardRepository;
import tn.oliveconnect.orchard.Plot;
import tn.oliveconnect.user.Delegation;
import tn.oliveconnect.user.FarmerProfile;
import tn.oliveconnect.user.Role;
import tn.oliveconnect.user.User;
import tn.oliveconnect.user.UserRepository;

class HarvestRequestControllerTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    HarvestRequestRepository harvestRequestRepository;
    @Autowired
    NotificationRepository notificationRepository;
    @Autowired
    OrchardRepository orchardRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    MongoTemplate mongoTemplate;

    @BeforeEach
    void clean() {
        notificationRepository.deleteAll();
        harvestRequestRepository.deleteAll();
        orchardRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void farmerCreatesRequestAndManagerReceivesNotification() throws Exception {
        User farmer = saveFarmer("ali@example.tn", "M-001");
        saveUser("manager@example.tn", EnumSet.of(Role.COLLECTION_MANAGER));
        String orchardId = saveOrchard(farmer.getId(), "North orchard");

        mockMvc.perform(post("/api/harvest-requests").header("Authorization", token("ali@example.tn"))
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson(orchardId, "URGENT")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.orchard.id").value(orchardId))
                .andExpect(jsonPath("$.orchard.name").value("North orchard"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.history[0].to").value("SUBMITTED"))
                .andExpect(jsonPath("$.allowedActions").value(org.hamcrest.Matchers.hasItems("MODIFY", "CANCEL")))
                .andExpect(jsonPath("$.version").value(0));

        mockMvc.perform(get("/api/notifications").header("Authorization", token("manager@example.tn")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("NEW_REQUEST"))
                .andExpect(jsonPath("$[0].read").value(false));
    }

    @Test
    void invalidDatesReturnAFieldError() throws Exception {
        User farmer = saveFarmer("ali@example.tn", "M-001");
        String orchardId = saveOrchard(farmer.getId(), "North orchard");

        mockMvc.perform(post("/api/harvest-requests").header("Authorization", token("ali@example.tn"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(orchardId, "PLANNED").replace("2026-11-25", "2026-11-28")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void anotherFarmersOrchardIsNotRevealed() throws Exception {
        User owner = saveFarmer("ali@example.tn", "M-001");
        saveFarmer("other@example.tn", "M-002");
        String orchardId = saveOrchard(owner.getId(), "North orchard");

        mockMvc.perform(post("/api/harvest-requests").header("Authorization", token("other@example.tn"))
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson(orchardId, "PLANNED")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void secondActiveRequestForAnOrchardReturnsConflict() throws Exception {
        User farmer = saveFarmer("ali@example.tn", "M-001");
        String orchardId = saveOrchard(farmer.getId(), "North orchard");
        String authorization = token("ali@example.tn");

        createRequest(authorization, orchardId, "PLANNED");
        mockMvc.perform(post("/api/harvest-requests").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson(orchardId, "URGENT")))
                .andExpect(status().isConflict());
    }

    @Test
    void managerCanValidateAndFarmerGetsNotification() throws Exception {
        User farmer = saveFarmer("ali@example.tn", "M-001");
        saveUser("manager@example.tn", EnumSet.of(Role.COLLECTION_MANAGER));
        String orchardId = saveOrchard(farmer.getId(), "North orchard");
        String requestId = createRequest(token("ali@example.tn"), orchardId, "PLANNED");

        mockMvc.perform(post("/api/harvest-requests/" + requestId + "/validate")
                        .header("Authorization", token("manager@example.tn")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.allowedActions").isEmpty());

        mockMvc.perform(get("/api/harvest-requests/" + requestId)
                        .header("Authorization", token("ali@example.tn")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedActions").value(org.hamcrest.Matchers.hasItems("MODIFY", "CANCEL")));

        mockMvc.perform(get("/api/notifications").header("Authorization", token("ali@example.tn")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("REQUEST_VALIDATED"));
    }

    @Test
    void rejectRequiresAReason() throws Exception {
        User farmer = saveFarmer("ali@example.tn", "M-001");
        saveUser("manager@example.tn", EnumSet.of(Role.COLLECTION_MANAGER));
        String orchardId = saveOrchard(farmer.getId(), "North orchard");
        String requestId = createRequest(token("ali@example.tn"), orchardId, "PLANNED");

        mockMvc.perform(post("/api/harvest-requests/" + requestId + "/reject")
                        .header("Authorization", token("manager@example.tn"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void managerCanRejectAndFarmerGetsTheReason() throws Exception {
        User farmer = saveFarmer("ali@example.tn", "M-001");
        saveUser("manager@example.tn", EnumSet.of(Role.COLLECTION_MANAGER));
        String orchardId = saveOrchard(farmer.getId(), "North orchard");
        String requestId = createRequest(token("ali@example.tn"), orchardId, "PLANNED");

        mockMvc.perform(post("/api/harvest-requests/" + requestId + "/reject")
                        .header("Authorization", token("manager@example.tn"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Window is already full\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Window is already full"));

        mockMvc.perform(get("/api/notifications?unread=true")
                        .header("Authorization", token("ali@example.tn")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("REQUEST_REJECTED"))
                .andExpect(jsonPath("$[0].message").value("Window is already full"));
    }

    @Test
    void farmerCanCancelSubmittedRequest() throws Exception {
        User farmer = saveFarmer("ali@example.tn", "M-001");
        String orchardId = saveOrchard(farmer.getId(), "North orchard");
        String requestId = createRequest(token("ali@example.tn"), orchardId, "PLANNED");

        mockMvc.perform(post("/api/harvest-requests/" + requestId + "/cancel")
                        .header("Authorization", token("ali@example.tn")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void staleUpdateReturnsStaleVersionConflict() throws Exception {
        User farmer = saveFarmer("ali@example.tn", "M-001");
        String orchardId = saveOrchard(farmer.getId(), "North orchard");
        String authorization = token("ali@example.tn");
        String requestId = createRequest(authorization, orchardId, "PLANNED");
        String update = "{\"desiredFrom\":\"2026-11-26\",\"desiredTo\":\"2026-11-28\","
                + "\"estimatedQuantityKg\":1100,\"version\":0}";

        mockMvc.perform(put("/api/harvest-requests/" + requestId).header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/harvest-requests/" + requestId).header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }

    @Test
    void notificationCanBeReadOnlyByItsRecipient() throws Exception {
        User farmer = saveFarmer("ali@example.tn", "M-001");
        saveUser("manager@example.tn", EnumSet.of(Role.COLLECTION_MANAGER));
        saveFarmer("other@example.tn", "M-002");
        String orchardId = saveOrchard(farmer.getId(), "North orchard");
        createRequest(token("ali@example.tn"), orchardId, "PLANNED");
        String notificationId = notificationRepository.findAll().get(0).getId();

        mockMvc.perform(post("/api/notifications/" + notificationId + "/read")
                        .header("Authorization", token("other@example.tn")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/notifications/" + notificationId + "/read")
                        .header("Authorization", token("manager@example.tn")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
        mockMvc.perform(post("/api/notifications/read-all")
                        .header("Authorization", token("manager@example.tn")))
                .andExpect(status().isNoContent());
    }

    @Test
    void harvestRequestIndexesAndNotificationTtlAreCreated() {
        List<String> requestIndexes = mongoTemplate.indexOps(HarvestRequest.class).getIndexInfo().stream()
                .map(IndexInfo::getName).toList();
        assertThat(requestIndexes).contains("farmer_created_at", "status_priority_score", "orchard_location_status",
                "orchard_active_request", "round_id");

        List<String> notificationIndexes = mongoTemplate.indexOps(tn.oliveconnect.notification.Notification.class)
                .getIndexInfo().stream().map(IndexInfo::getName).toList();
        assertThat(notificationIndexes).contains("recipient_read_created_at", "created_at_ttl");
    }

    private String createRequest(String authorization, String orchardId, String type) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/harvest-requests").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson(orchardId, type)))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String requestJson(String orchardId, String type) {
        return "{\"orchardId\":\"" + orchardId + "\",\"type\":\"" + type
                + "\",\"desiredFrom\":\"2026-11-25\",\"desiredTo\":\"2026-11-27\","
                + "\"estimatedQuantityKg\":900,\"comment\":\"Rain forecast\"}";
    }

    private String saveOrchard(String ownerId, String name) {
        Orchard orchard = new Orchard();
        orchard.setOwnerId(ownerId);
        orchard.setName(name);
        orchard.setDelegation(Delegation.SAKIET_EZZIT.name());
        orchard.setLocation(new GeoPoint(10.7603, 34.7998));
        Plot plot = new Plot();
        plot.setCode("P1");
        plot.setVarietyCode("CHEMLALI");
        plot.setAreaHa(2.0);
        plot.setTreeCount(200);
        orchard.setPlots(List.of(plot));
        orchard.setCurrentMaturityIndex(3.8);
        orchard.setLastSeasonYieldKgHa(2100);
        orchard.recomputeTotals();
        return orchardRepository.save(orchard).getId();
    }

    private User saveFarmer(String email, String memberNumber) {
        User user = saveUser(email, EnumSet.of(Role.FARMER));
        FarmerProfile profile = new FarmerProfile();
        profile.setMemberNumber(memberNumber);
        profile.setCin("12345678");
        profile.setDelegation(Delegation.SFAX_VILLE);
        user.setFarmerProfile(profile);
        return userRepository.save(user);
    }

    private User saveUser(String email, EnumSet<Role> roles) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("secret123"));
        user.setFirstName("Test");
        user.setLastName("User");
        user.setRoles(roles);
        return userRepository.save(user);
    }

    private String token(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk()).andReturn();
        return "Bearer " + JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }
}
