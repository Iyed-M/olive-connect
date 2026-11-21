package tn.oliveconnect.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tn.oliveconnect.AbstractIntegrationTest;
import tn.oliveconnect.harvest.HarvestRequest;
import tn.oliveconnect.harvest.HarvestRequestRepository;
import tn.oliveconnect.harvest.HarvestType;
import tn.oliveconnect.notification.NotificationRepository;
import tn.oliveconnect.orchard.GeoPoint;
import tn.oliveconnect.orchard.Orchard;
import tn.oliveconnect.orchard.OrchardRepository;
import tn.oliveconnect.user.Role;
import tn.oliveconnect.user.User;
import tn.oliveconnect.user.UserRepository;

class FieldAlertControllerTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    FieldAlertRepository alertRepository;
    @Autowired
    OrchardRepository orchardRepository;
    @Autowired
    HarvestRequestRepository harvestRequestRepository;
    @Autowired
    NotificationRepository notificationRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    GridFsTemplate gridFsTemplate;
    @Autowired
    MongoTemplate mongoTemplate;

    @BeforeEach
    void clean() {
        gridFsTemplate.delete(new Query());
        alertRepository.deleteAll();
        harvestRequestRepository.deleteAll();
        notificationRepository.deleteAll();
        orchardRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void farmerReportsNearOrchardWithPhotoAndManagerGetsNotification() throws Exception {
        User farmer = saveUser("farmer-alert@example.tn", Role.FARMER);
        saveUser("manager-alert@example.tn", Role.COLLECTION_MANAGER);
        Orchard orchard = saveOrchard(farmer, "North orchard", 10.7603, 34.7998);

        String alertId = report(token(farmer), orchard, "MATURITY", 10.7603, 34.7998,
                new MockMultipartFile("photo", "olive.png", "image/png", new byte[] {1, 2, 3}));

        mockMvc.perform(get("/api/alerts/" + alertId).header("Authorization", token(farmer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.source").value("FARMER"))
                .andExpect(jsonPath("$.locationMismatch").value(false))
                .andExpect(jsonPath("$.photoUrls.length()").value(1));

        assertThat(notificationRepository.findAll()).anyMatch(notification ->
                notification.getType().name().equals("NEW_ALERT"));
        org.bson.Document stored = mongoTemplate.getDb().getCollection("alerts.files")
                .find(new org.bson.Document("metadata.alertId", alertId)).first();
        assertThat(stored).isNotNull();
    }

    @Test
    void reportFiveKilometresAwayIsKeptAndFlagged() throws Exception {
        User farmer = saveUser("far-away@example.tn", Role.FARMER);
        Orchard orchard = saveOrchard(farmer, "Far orchard", 10.7603, 34.7998);

        String alertId = report(token(farmer), orchard, "PEST", 10.815, 34.7998, null);

        mockMvc.perform(get("/api/alerts/" + alertId).header("Authorization", token(farmer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationMismatch").value(true));
    }

    @Test
    void invalidPhotoTypeAndSizeReturnBadRequest() throws Exception {
        User farmer = saveUser("bad-photo@example.tn", Role.FARMER);
        Orchard orchard = saveOrchard(farmer, "Photo orchard", 10.7603, 34.7998);
        String authorization = token(farmer);

        MockMultipartFile text = new MockMultipartFile("photo", "notes.txt", "text/plain", new byte[] {1});
        mockMvc.perform(reportRequest(authorization, orchard, "OTHER", 10.7603, 34.7998, text))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        MockMultipartFile tooLarge = new MockMultipartFile("photo", "large.jpg", "image/jpeg",
                new byte[5 * 1024 * 1024 + 1]);
        mockMvc.perform(reportRequest(authorization, orchard, "OTHER", 10.7603, 34.7998, tooLarge))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(alertRepository.findAll()).isEmpty();
    }

    @Test
    void secondOpenAlertOfSameTypeReturnsConflict() throws Exception {
        User farmer = saveUser("duplicate-alert@example.tn", Role.FARMER);
        Orchard orchard = saveOrchard(farmer, "Duplicate orchard", 10.7603, 34.7998);
        String authorization = token(farmer);
        report(authorization, orchard, "DISEASE", 10.7603, 34.7998, null);

        mockMvc.perform(reportRequest(authorization, orchard, "DISEASE", 10.7603, 34.7998, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_ALERT_EXISTS"));
    }

    @Test
    void concurrentReportsOfSameTypeAllowOnlyOneOpenAlert() throws Exception {
        User farmer = saveUser("concurrent-alert@example.tn", Role.FARMER);
        Orchard orchard = saveOrchard(farmer, "Concurrent orchard", 10.7603, 34.7998);
        String authorization = token(farmer);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> reports = List.of(
                    () -> mockMvc.perform(reportRequest(authorization, orchard, "RAIN", 10.7603, 34.7998, null))
                            .andReturn().getResponse().getStatus(),
                    () -> mockMvc.perform(reportRequest(authorization, orchard, "RAIN", 10.7603, 34.7998, null))
                            .andReturn().getResponse().getStatus());
            List<Future<Integer>> futures = executor.invokeAll(reports);
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get());
            }

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(alertRepository.findAll()).singleElement()
                    .satisfies(alert -> assertThat(alert.getOpenAlertKey())
                            .isEqualTo(orchard.getId() + ":RAIN"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void farmerReportReachesConnectedManagerWithinTwoSeconds() throws Exception {
        User farmer = saveUser("report-sse-farmer@example.tn", Role.FARMER);
        User manager = saveUser("report-sse-manager@example.tn", Role.COLLECTION_MANAGER);
        Orchard orchard = saveOrchard(farmer, "SSE orchard", 10.7603, 34.7998);
        String managerTicket = eventTicket(token(manager));
        MvcResult stream = mockMvc.perform(get("/api/events").param("ticket", managerTicket)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        report(token(farmer), orchard, "MATURITY", 10.7603, 34.7998, null);

        waitForResponse(stream, "event:alert");
        assertThat(stream.getResponse().getContentAsString()).contains("NEW_ALERT");
    }

    @Test
    void nearestEndpointReturnsNearestOwnOrchardOnly() throws Exception {
        User farmer = saveUser("nearest-farmer@example.tn", Role.FARMER);
        User otherFarmer = saveUser("nearest-other@example.tn", Role.FARMER);
        Orchard ownFar = saveOrchard(farmer, "Own far", 10.80, 34.80);
        Orchard ownNear = saveOrchard(farmer, "Own near", 10.761, 34.7998);
        saveOrchard(otherFarmer, "Other nearest", 10.7601, 34.7998);

        mockMvc.perform(get("/api/orchards/nearest?lng=10.7603&lat=34.7998")
                        .header("Authorization", token(farmer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ownNear.getId()))
                .andExpect(jsonPath("$.name").value("Own near"));
        assertThat(ownFar.getId()).isNotEqualTo(ownNear.getId());
    }

    @Test
    void managerCanAcknowledgeResolveDismissAndCreateOneUrgentRequest() throws Exception {
        User farmer = saveUser("handle-farmer@example.tn", Role.FARMER);
        User manager = saveUser("handle-manager@example.tn", Role.COLLECTION_MANAGER);
        Orchard orchard = saveOrchard(farmer, "Ripe orchard", 10.7603, 34.7998);
        String farmerAuthorization = token(farmer);
        String managerAuthorization = token(manager);

        String maturityAlert = report(farmerAuthorization, orchard, "MATURITY", 10.7603, 34.7998, null);
        mockMvc.perform(post("/api/alerts/" + maturityAlert + "/acknowledge")
                        .header("Authorization", managerAuthorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
        mockMvc.perform(post("/api/alerts/" + maturityAlert + "/create-urgent-request")
                        .header("Authorization", managerAuthorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.harvestRequestId").isNotEmpty());

        assertThat(harvestRequestRepository.findAll()).singleElement()
                .satisfies(request -> assertThat(request.getType()).isEqualTo(HarvestType.URGENT));

        String pestAlert = report(farmerAuthorization, orchard, "PEST", 10.7603, 34.7998, null);
        mockMvc.perform(post("/api/alerts/" + pestAlert + "/dismiss")
                        .header("Authorization", managerAuthorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolution\":\"False alarm\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"));

        String rainAlert = report(farmerAuthorization, orchard, "RAIN", 10.7603, 34.7998, null);
        mockMvc.perform(post("/api/alerts/" + rainAlert + "/resolve")
                        .header("Authorization", managerAuthorization))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
    }

    @Test
    void farmerCannotHandleAnotherFarmersAlertAndManagerCanListAll() throws Exception {
        User first = saveUser("first-alert@example.tn", Role.FARMER);
        User second = saveUser("second-alert@example.tn", Role.FARMER);
        User manager = saveUser("list-manager@example.tn", Role.COLLECTION_MANAGER);
        Orchard firstOrchard = saveOrchard(first, "First orchard", 10.7603, 34.7998);
        Orchard secondOrchard = saveOrchard(second, "Second orchard", 10.77, 34.7998);
        String firstAlert = report(token(first), firstOrchard, "RAIN", 10.7603, 34.7998, null);
        report(token(second), secondOrchard, "PEST", 10.77, 34.7998, null);

        mockMvc.perform(get("/api/alerts").header("Authorization", token(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(firstAlert));
        mockMvc.perform(get("/api/alerts").header("Authorization", token(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/alerts/" + firstAlert).header("Authorization", token(second)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/alerts/" + firstAlert + "/acknowledge")
                        .header("Authorization", token(first)))
                .andExpect(status().isForbidden());
    }

    private String report(String authorization, Orchard orchard, String type, double longitude, double latitude,
                          MockMultipartFile photo) throws Exception {
        MvcResult result = mockMvc.perform(reportRequest(authorization, orchard, type, longitude, latitude, photo))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private MockMultipartHttpServletRequestBuilder reportRequest(
            String authorization, Orchard orchard, String type, double longitude, double latitude,
            MockMultipartFile photo) {
        MockMultipartFile data = new MockMultipartFile("data", "report.json", MediaType.APPLICATION_JSON_VALUE,
                reportJson(orchard.getId(), type, longitude, latitude).getBytes(StandardCharsets.UTF_8));
        MockMultipartHttpServletRequestBuilder request = multipart("/api/alerts");
        request.file(data).header("Authorization", authorization);
        if (photo != null) {
            request.file(photo);
        }
        return request;
    }

    private String reportJson(String orchardId, String type, double longitude, double latitude) {
        return "{\"type\":\"" + type + "\",\"orchardId\":\"" + orchardId
                + "\",\"location\":{\"type\":\"Point\",\"coordinates\":["
                + longitude + "," + latitude + "]},\"description\":\"Field report\"}";
    }

    private Orchard saveOrchard(User owner, String name, double longitude, double latitude) {
        Orchard orchard = new Orchard();
        orchard.setOwnerId(owner.getId());
        orchard.setName(name);
        orchard.setLocation(new GeoPoint(longitude, latitude));
        orchard.setLastSeasonYieldKgHa(1000);
        return orchardRepository.save(orchard);
    }

    private User saveUser(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("secret123"));
        user.setFirstName("Test");
        user.setLastName("User");
        user.setRoles(EnumSet.of(role));
        return userRepository.save(user);
    }

    private String token(User user) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk()).andReturn();
        return "Bearer " + JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String eventTicket(String authorization) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/events/ticket")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.ticket");
    }

    private void waitForResponse(MvcResult result, String text) throws Exception {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (!result.getResponse().getContentAsString().contains(text)
                && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(result.getResponse().getContentAsString()).contains(text);
    }
}
