package tn.oliveconnect.round;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tn.oliveconnect.AbstractIntegrationTest;
import tn.oliveconnect.common.StatusChange;
import tn.oliveconnect.harvest.HarvestRequest;
import tn.oliveconnect.harvest.HarvestRequestRepository;
import tn.oliveconnect.harvest.HarvestRequestStatus;
import tn.oliveconnect.harvest.HarvestType;
import tn.oliveconnect.harvest.OrchardSnapshot;
import tn.oliveconnect.notification.NotificationRepository;
import tn.oliveconnect.notification.NotificationType;
import tn.oliveconnect.orchard.GeoPoint;
import tn.oliveconnect.orchard.HarvestStatus;
import tn.oliveconnect.orchard.Orchard;
import tn.oliveconnect.orchard.OrchardRepository;
import tn.oliveconnect.user.Role;
import tn.oliveconnect.user.User;
import tn.oliveconnect.user.UserRepository;

class CollectionRoundExecutionControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired CollectionRoundRepository roundRepository;
    @Autowired HarvestRequestRepository requestRepository;
    @Autowired OrchardRepository orchardRepository;
    @Autowired UserRepository userRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void clean() {
        notificationRepository.deleteAll();
        roundRepository.deleteAll();
        requestRepository.deleteAll();
        orchardRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void assignedTeamLeaderStartsTodaysRoundAndNotifiesFarmers() throws Exception {
        ExecutionFixture fixture = fixture(LocalDate.now(ZoneOffset.UTC), 1);

        mockMvc.perform(post("/api/rounds/" + fixture.round().getId() + "/start")
                        .header("Authorization", token(fixture.leader().getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        assertThat(roundRepository.findById(fixture.round().getId()).orElseThrow().getStatus())
                .isEqualTo(RoundStatus.IN_PROGRESS);
        assertThat(requestRepository.findById(fixture.requests().get(0).getId()).orElseThrow().getStatus())
                .isEqualTo(HarvestRequestStatus.IN_PROGRESS);
        assertThat(orchardRepository.findById(fixture.orchards().get(0).getId()).orElseThrow().getHarvestStatus())
                .isEqualTo(HarvestStatus.IN_PROGRESS);
        assertThat(notificationRepository.findAll()).anyMatch(notification ->
                notification.getRecipientId().equals(fixture.farmer().getId())
                        && notification.getType() == NotificationType.ROUND_STARTED
                        && notification.getLink().equals("/farmer/requests"));
    }

    @Test
    void anotherTeamLeaderCannotStartTheRound() throws Exception {
        ExecutionFixture fixture = fixture(LocalDate.now(ZoneOffset.UTC), 1);

        mockMvc.perform(post("/api/rounds/" + fixture.round().getId() + "/start")
                        .header("Authorization", token(fixture.otherLeader().getEmail())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(roundRepository.findById(fixture.round().getId()).orElseThrow().getStatus())
                .isEqualTo(RoundStatus.PLANNED);
    }

    @Test
    void roundCannotStartBeforeItsDate() throws Exception {
        ExecutionFixture fixture = fixture(LocalDate.now(ZoneOffset.UTC).plusDays(1), 1);

        mockMvc.perform(post("/api/rounds/" + fixture.round().getId() + "/start")
                        .header("Authorization", token(fixture.leader().getEmail())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_NOT_TODAY"));

        assertThat(roundRepository.findById(fixture.round().getId()).orElseThrow().getStatus())
                .isEqualTo(RoundStatus.PLANNED);
    }

    @Test
    void completingWithPendingStopsIsRejected() throws Exception {
        ExecutionFixture fixture = fixture(LocalDate.now(ZoneOffset.UTC), 1);
        String leaderToken = token(fixture.leader().getEmail());

        mockMvc.perform(post("/api/rounds/" + fixture.round().getId() + "/start")
                        .header("Authorization", leaderToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/rounds/" + fixture.round().getId() + "/complete")
                        .header("Authorization", leaderToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("STOPS_PENDING"));

        assertThat(roundRepository.findById(fixture.round().getId()).orElseThrow().getStatus())
                .isEqualTo(RoundStatus.IN_PROGRESS);
    }

    @Test
    void leaderCanFinishDoneAndSkippedStopsWithCascadedStatuses() throws Exception {
        ExecutionFixture fixture = fixture(LocalDate.now(ZoneOffset.UTC), 2);
        String leaderToken = token(fixture.leader().getEmail());
        String roundId = fixture.round().getId();

        mockMvc.perform(post("/api/rounds/" + roundId + "/start").header("Authorization", leaderToken))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/rounds/" + roundId + "/stops/1")
                        .header("Authorization", leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stops[0].status").value("IN_PROGRESS"));
        mockMvc.perform(patch("/api/rounds/" + roundId + "/stops/1")
                        .header("Authorization", leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\",\"harvestedKg\":3200,\"crateCount\":80}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stops[0].status").value("DONE"));
        mockMvc.perform(patch("/api/rounds/" + roundId + "/stops/2")
                        .header("Authorization", leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SKIPPED\",\"comment\":\"Orchard inaccessible\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stops[1].status").value("SKIPPED"));

        mockMvc.perform(post("/api/rounds/" + roundId + "/complete")
                        .header("Authorization", leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalHarvestedKg").value(3200.0));

        assertThat(requestRepository.findById(fixture.requests().get(0).getId()).orElseThrow().getStatus())
                .isEqualTo(HarvestRequestStatus.COMPLETED);
        HarvestRequest skipped = requestRepository.findById(fixture.requests().get(1).getId()).orElseThrow();
        assertThat(skipped.getStatus()).isEqualTo(HarvestRequestStatus.VALIDATED);
        assertThat(skipped.getRoundId()).isNull();
        assertThat(orchardRepository.findById(fixture.orchards().get(0).getId()).orElseThrow().getHarvestStatus())
                .isEqualTo(HarvestStatus.HARVESTED);
        assertThat(orchardRepository.findById(fixture.orchards().get(1).getId()).orElseThrow().getHarvestStatus())
                .isEqualTo(HarvestStatus.NOT_HARVESTED);
        assertThat(notificationRepository.findAll()).anyMatch(notification ->
                notification.getRecipientId().equals(fixture.farmer().getId())
                        && notification.getType() == NotificationType.ROUND_COMPLETED
                        && notification.getLink().equals("/farmer/requests"));
    }

    @Test
    void stopCompletionRequiresQuantityAndCratesAndSkippingRequiresAReason() throws Exception {
        ExecutionFixture fixture = fixture(LocalDate.now(ZoneOffset.UTC), 1);
        String leaderToken = token(fixture.leader().getEmail());
        String roundId = fixture.round().getId();

        mockMvc.perform(post("/api/rounds/" + roundId + "/start").header("Authorization", leaderToken))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/rounds/" + roundId + "/stops/1")
                        .header("Authorization", leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/rounds/" + roundId + "/stops/1")
                        .header("Authorization", leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\",\"crateCount\":80}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(patch("/api/rounds/" + roundId + "/stops/1")
                        .header("Authorization", leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SKIPPED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private ExecutionFixture fixture(LocalDate date, int stopCount) {
        User farmer = saveUser("farmer@execution-test.tn", Role.FARMER);
        User leader = saveUser("leader@execution-test.tn", Role.TEAM_LEADER);
        User otherLeader = saveUser("other-leader@execution-test.tn", Role.TEAM_LEADER);
        List<Orchard> orchards = new java.util.ArrayList<>();
        List<HarvestRequest> requests = new java.util.ArrayList<>();
        for (int i = 1; i <= stopCount; i++) {
            Orchard orchard = saveOrchard(farmer, "Execution orchard " + i);
            orchards.add(orchard);
            requests.add(saveScheduledRequest(farmer, orchard, date));
        }

        CollectionRound round = new CollectionRound();
        round.setCode("T-EXEC-" + stopCount + "-" + date);
        round.setDate(date);
        round.setStartTime(LocalTime.of(7, 0));
        round.setEndTime(LocalTime.of(15, 0));
        round.setStatus(RoundStatus.PLANNED);
        round.setPlannedById("planning-manager");
        round.setTeamLeaderId(leader.getId());
        round.setMillId("mill-execution");
        round.setStops(requests.stream().map(request ->
                RoundStop.from(request, requests.indexOf(request) + 1, LocalTime.of(7, 30))).toList());
        round.setEstimatedTotalKg(requests.stream().mapToDouble(HarvestRequest::getEstimatedQuantityKg).sum());
        round.setSeason("2026-2027");
        round.setStatusHistory(List.of(new StatusChange(null, RoundStatus.PLANNED.name(), Instant.now(),
                "planning-manager", null)));
        round = roundRepository.save(round);
        for (HarvestRequest request : requests) {
            request.setRoundId(round.getId());
            requestRepository.save(request);
        }
        return new ExecutionFixture(farmer, leader, otherLeader, orchards, requests, round);
    }

    private Orchard saveOrchard(User farmer, String name) {
        Orchard orchard = new Orchard();
        orchard.setName(name);
        orchard.setOwnerId(farmer.getId());
        orchard.setLocation(new GeoPoint(10.76, 34.80));
        orchard.setDelegation("SAKIET_EZZIT");
        return orchardRepository.save(orchard);
    }

    private HarvestRequest saveScheduledRequest(User farmer, Orchard orchard, LocalDate date) {
        HarvestRequest request = new HarvestRequest();
        request.setFarmerId(farmer.getId());
        request.setOrchardId(orchard.getId());
        request.setOrchard(new OrchardSnapshot(orchard.getId(), orchard.getName(), orchard.getLocation(),
                orchard.getDelegation(), "CHEMLALI", 2, 2100, 3.8));
        request.setType(HarvestType.PLANNED);
        request.setDesiredFrom(date.minusDays(1));
        request.setDesiredTo(date.plusDays(1));
        request.setEstimatedQuantityKg(900);
        request.setCreatedAt(Instant.now());
        request.transitionTo(HarvestRequestStatus.SUBMITTED, farmer.getId(), null);
        request.transitionTo(HarvestRequestStatus.VALIDATED, "planning-manager", null);
        request.transitionTo(HarvestRequestStatus.SCHEDULED, "planning-manager", "scheduled");
        return requestRepository.save(request);
    }

    private User saveUser(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("secret123"));
        user.setFirstName("Execution");
        user.setLastName(role.name());
        user.setRoles(EnumSet.of(role));
        return userRepository.save(user);
    }

    private String token(String email) throws Exception {
        return "Bearer " + com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + email + "\",\"password\":\"secret123\"}"))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.accessToken");
    }

    private record ExecutionFixture(User farmer, User leader, User otherLeader, List<Orchard> orchards,
                                    List<HarvestRequest> requests, CollectionRound round) {
    }
}
