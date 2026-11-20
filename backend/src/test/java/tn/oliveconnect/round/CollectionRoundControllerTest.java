package tn.oliveconnect.round;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import tn.oliveconnect.harvest.HarvestRequest;
import tn.oliveconnect.harvest.HarvestRequestRepository;
import tn.oliveconnect.harvest.HarvestRequestStatus;
import tn.oliveconnect.harvest.HarvestType;
import tn.oliveconnect.harvest.OrchardSnapshot;
import tn.oliveconnect.notification.NotificationRepository;
import tn.oliveconnect.notification.NotificationType;
import tn.oliveconnect.orchard.GeoPoint;
import tn.oliveconnect.resource.Bin;
import tn.oliveconnect.resource.ResourceBooking;
import tn.oliveconnect.resource.ResourceBookingRepository;
import tn.oliveconnect.resource.EquipmentRepository;
import tn.oliveconnect.resource.Worker;
import tn.oliveconnect.resource.WorkerRepository;
import tn.oliveconnect.mill.Mill;
import tn.oliveconnect.mill.MillRepository;
import tn.oliveconnect.user.Role;
import tn.oliveconnect.user.User;
import tn.oliveconnect.user.UserRepository;

class CollectionRoundControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired CollectionRoundRepository roundRepository;
    @Autowired ResourceBookingRepository bookingRepository;
    @Autowired HarvestRequestRepository requestRepository;
    @Autowired UserRepository userRepository;
    @Autowired WorkerRepository workerRepository;
    @Autowired EquipmentRepository equipmentRepository;
    @Autowired MillRepository millRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired MongoTemplate mongoTemplate;

    @BeforeEach
    void clean() {
        notificationRepository.deleteAll();
        bookingRepository.deleteAll();
        roundRepository.deleteAll();
        requestRepository.deleteAll();
        workerRepository.deleteAll();
        equipmentRepository.deleteAll();
        millRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void collectionManagerPlansValidatedRequestAndBooksResources() throws Exception {
        User manager = saveUser("manager@round-test.tn", Role.COLLECTION_MANAGER);
        User farmer = saveUser("farmer@round-test.tn", Role.FARMER);
        User leader = saveUser("leader@round-test.tn", Role.TEAM_LEADER);
        Worker worker = saveWorker("11111111");
        Bin bin = saveBin("B-07");
        Mill mill = saveMill("Sfax North", 10000);
        HarvestRequest request = saveValidatedRequest(farmer, manager, "Orchard North", 900);

        String body = planJson(request, leader, worker, bin, mill);

        mockMvc.perform(post("/api/rounds").header("Authorization", token(manager.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.stops[0].harvestRequestId").value(request.getId()))
                .andExpect(jsonPath("$.warnings").isArray());

        CollectionRound round = roundRepository.findAll().get(0);
        assertThat(round.getTeamLeaderId()).isEqualTo(leader.getId());
        assertThat(round.getEquipmentIds()).containsExactly(bin.getId());
        assertThat(requestRepository.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(HarvestRequestStatus.SCHEDULED);
        assertThat(requestRepository.findById(request.getId()).orElseThrow().getRoundId())
                .isEqualTo(round.getId());
        assertThat(bookingRepository.findByRoundId(round.getId())).hasSize(3);
        assertThat(bookingRepository.findByRoundId(round.getId()).stream()
                .map(ResourceBooking::getResourceKind))
                .containsExactlyInAnyOrder(tn.oliveconnect.resource.ResourceKind.TEAM_LEADER,
                        tn.oliveconnect.resource.ResourceKind.WORKER,
                        tn.oliveconnect.resource.ResourceKind.EQUIPMENT);
        assertThat(notificationRepository.findAll().stream().map(n -> n.getType()))
                .contains(NotificationType.ROUND_SCHEDULED, NotificationType.ROUND_ASSIGNED);
    }

    @Test
    void planningSuggestionsExposeTeamLeadersAndGroupRequestIds() throws Exception {
        User manager = saveUser("manager@suggestions-test.tn", Role.COLLECTION_MANAGER);
        User farmer = saveUser("farmer@suggestions-test.tn", Role.FARMER);
        User leader = saveUser("leader@suggestions-test.tn", Role.TEAM_LEADER);
        HarvestRequest request = saveValidatedRequest(farmer, manager, "Suggestion orchard", 900);

        mockMvc.perform(get("/api/planning/suggestions")
                        .param("date", "2026-11-25")
                        .header("Authorization", token(manager.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamLeaders[0].id").value(leader.getId()))
                .andExpect(jsonPath("$.groups[0].requestIds[0]").value(request.getId()));
    }

    @Test
    void roundDateOutsideRequestWindowIsRejected() throws Exception {
        User manager = saveUser("manager@window-test.tn", Role.COLLECTION_MANAGER);
        User farmer = saveUser("farmer@window-test.tn", Role.FARMER);
        User leader = saveUser("leader@window-test.tn", Role.TEAM_LEADER);
        Worker worker = saveWorker("11111111");
        Bin bin = saveBin("B-07");
        Mill mill = saveMill("Sfax North", 10000);
        HarvestRequest request = saveValidatedRequest(farmer, manager, "Window orchard", 900);

        mockMvc.perform(post("/api/rounds").header("Authorization", token(manager.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson(request, leader, worker, bin, mill).replace("2026-11-25", "2026-11-28")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REQUEST_OUTSIDE_WINDOW"));
    }

    @Test
    void roundUpdateRejectsAnEmptyStopList() throws Exception {
        User manager = saveUser("manager@update-test.tn", Role.COLLECTION_MANAGER);
        User farmer = saveUser("farmer@update-test.tn", Role.FARMER);
        User leader = saveUser("leader@update-test.tn", Role.TEAM_LEADER);
        Worker worker = saveWorker("11111111");
        Bin bin = saveBin("B-07");
        Mill mill = saveMill("Sfax North", 10000);
        HarvestRequest request = saveValidatedRequest(farmer, manager, "Update orchard", 900);
        MvcResult created = mockMvc.perform(post("/api/rounds").header("Authorization", token(manager.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON).content(planJson(request, leader, worker, bin, mill)))
                .andExpect(status().isCreated()).andReturn();
        String roundId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        int version = JsonPath.read(created.getResponse().getContentAsString(), "$.version");

        mockMvc.perform(put("/api/rounds/" + roundId).header("Authorization", token(manager.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestIds\":[],\"version\":" + version + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ROUND_EMPTY"));
    }

    @Test
    void sameTeamLeaderCannotBeBookedTwiceForOverlappingRounds() throws Exception {
        User manager = saveUser("manager@leader-conflict.tn", Role.COLLECTION_MANAGER);
        User farmerOne = saveUser("farmer-one@leader-conflict.tn", Role.FARMER);
        User farmerTwo = saveUser("farmer-two@leader-conflict.tn", Role.FARMER);
        User leader = saveUser("leader@leader-conflict.tn", Role.TEAM_LEADER);
        Worker firstWorker = saveWorker("11111111");
        Worker secondWorker = saveWorker("22222222");
        Bin firstBin = saveBin("B-07");
        Bin secondBin = saveBin("B-08");
        Mill mill = saveMill("Sfax North", 10000);
        HarvestRequest firstRequest = saveValidatedRequest(farmerOne, manager, "First leader orchard", 900);
        HarvestRequest secondRequest = saveValidatedRequest(farmerTwo, manager, "Second leader orchard", 900);

        mockMvc.perform(post("/api/rounds").header("Authorization", token(manager.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson(firstRequest, leader, firstWorker, firstBin, mill)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/rounds").header("Authorization", token(manager.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson(secondRequest, leader, secondWorker, secondBin, mill)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_CONFLICT"));
    }

    @Test
    void overlappingBookingReturnsConflictAndLeavesTheRequestUnchanged() throws Exception {
        User manager = saveUser("manager@round-test.tn", Role.COLLECTION_MANAGER);
        User farmer = saveUser("farmer@round-test.tn", Role.FARMER);
        User leader = saveUser("leader@round-test.tn", Role.TEAM_LEADER);
        Worker worker = saveWorker("11111111");
        Bin bin = saveBin("B-07");
        Mill mill = saveMill("Sfax North", 10000);
        HarvestRequest existingRequest = saveValidatedRequest(farmer, manager, "Existing orchard", 900);
        HarvestRequest newRequest = saveValidatedRequest(farmer, manager, "New orchard", 900);
        CollectionRound existingRound = new CollectionRound();
        existingRound.setDate(LocalDate.of(2026, 11, 25));
        existingRound.setStartTime(LocalTime.of(7, 0));
        existingRound.setEndTime(LocalTime.of(15, 0));
        existingRound.setTeamLeaderId(leader.getId());
        existingRound.setMillId(mill.getId());
        existingRound.setStatus(RoundStatus.PLANNED);
        existingRound.setStops(List.of(stopFor(existingRequest)));
        existingRound = roundRepository.save(existingRound);
        bookingRepository.save(ResourceBooking.forEquipment(bin.getId(), bin.getType(), existingRound.getId(),
                Instant.parse("2026-11-25T07:00:00Z"), Instant.parse("2026-11-25T15:00:00Z")));

        mockMvc.perform(post("/api/rounds").header("Authorization", token(manager.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson(newRequest, leader, worker, bin, mill)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_CONFLICT"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("B-07")));

        assertThat(roundRepository.findAll()).hasSize(1);
        assertThat(bookingRepository.findAll()).hasSize(1);
        assertThat(requestRepository.findById(newRequest.getId()).orElseThrow().getStatus())
                .isEqualTo(HarvestRequestStatus.VALIDATED);
    }

    @Test
    void lowMillCapacityIsReturnedAsAWarning() throws Exception {
        User manager = saveUser("manager@round-test.tn", Role.COLLECTION_MANAGER);
        User farmer = saveUser("farmer@round-test.tn", Role.FARMER);
        User leader = saveUser("leader@round-test.tn", Role.TEAM_LEADER);
        Worker worker = saveWorker("11111111");
        Bin bin = saveBin("B-07");
        Mill mill = saveMill("Sfax North", 100);
        HarvestRequest request = saveValidatedRequest(farmer, manager, "Orchard North", 900);

        mockMvc.perform(post("/api/rounds").header("Authorization", token(manager.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON).content(planJson(request, leader, worker, bin, mill)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warnings[0].code").value("MILL_CAPACITY_LOW"));
    }

    @Test
    void farmerCannotPlanARound() throws Exception {
        User farmer = saveUser("farmer@round-test.tn", Role.FARMER);
        mockMvc.perform(post("/api/rounds").header("Authorization", token(farmer.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-11-25\",\"startTime\":\"07:00\","
                                + "\"endTime\":\"15:00\",\"teamLeaderId\":\"missing\","
                                + "\"requestIds\":[\"missing\"],\"millId\":\"missing\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void concurrentPlansForTheSameBinHaveOneWinner() throws Exception {
        User firstManager = saveUser("manager-one@round-test.tn", Role.COLLECTION_MANAGER);
        User secondManager = saveUser("manager-two@round-test.tn", Role.COLLECTION_MANAGER);
        User firstFarmer = saveUser("farmer-one@round-test.tn", Role.FARMER);
        User secondFarmer = saveUser("farmer-two@round-test.tn", Role.FARMER);
        User leader = saveUser("leader@round-test.tn", Role.TEAM_LEADER);
        Worker firstWorker = saveWorker("11111111");
        Worker secondWorker = saveWorker("22222222");
        Bin bin = saveBin("B-07");
        Mill mill = saveMill("Sfax North", 10000);
        HarvestRequest firstRequest = saveValidatedRequest(firstFarmer, firstManager, "First orchard", 900);
        HarvestRequest secondRequest = saveValidatedRequest(secondFarmer, secondManager, "Second orchard", 900);
        String firstToken = token(firstManager.getEmail());
        String secondToken = token(secondManager.getEmail());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Integer> first = executor.submit(() -> performPlan(start, firstToken,
                planJson(firstRequest, leader, firstWorker, bin, mill)));
        Future<Integer> second = executor.submit(() -> performPlan(start, secondToken,
                planJson(secondRequest, leader, secondWorker, bin, mill)));
        start.countDown();
        int firstStatus = first.get();
        int secondStatus = second.get();
        executor.shutdownNow();

        assertThat(List.of(firstStatus, secondStatus)).containsExactlyInAnyOrder(201, 409);
        assertThat(roundRepository.findAll()).hasSize(1);
        assertThat(bookingRepository.findByResourceIdAndStatus(bin.getId(),
                tn.oliveconnect.resource.BookingStatus.ACTIVE)).hasSize(1);
    }

    @Test
    void planningCollectionsHaveTheCalendarAndOverlapIndexes() {
        assertThat(mongoTemplate.indexOps(CollectionRound.class).getIndexInfo().stream()
                .map(IndexInfo::getName))
                .contains("code", "date_status", "team_leader_date", "stops_harvest_request_id",
                        "worker_ids", "equipment_ids");
        assertThat(mongoTemplate.indexOps(ResourceBooking.class).getIndexInfo().stream()
                .map(IndexInfo::getName))
                .contains("resource_id_start_end_active", "start_resource_type", "round_id");
        assertThat(mongoTemplate.indexOps(Mill.class).getIndexInfo().stream()
                .map(IndexInfo::getName)).contains("location_2dsphere");
    }

    private int performPlan(CountDownLatch start, String authorization, String body) {
        try {
            start.await();
            return mockMvc.perform(post("/api/rounds").header("Authorization", authorization)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andReturn().getResponse().getStatus();
        } catch (Exception ex) {
            return 599;
        }
    }

    private String planJson(HarvestRequest request, User leader, Worker worker, Bin bin, Mill mill) {
        return """
                {"date":"2026-11-25","startTime":"07:00","endTime":"15:00",
                 "teamLeaderId":"%s","requestIds":["%s"],"workerIds":["%s"],
                 "equipmentIds":["%s"],"millId":"%s"}
                """.formatted(leader.getId(), request.getId(), worker.getId(), bin.getId(), mill.getId());
    }

    private HarvestRequest saveValidatedRequest(User farmer, User manager, String name, double estimatedKg) {
        HarvestRequest request = new HarvestRequest();
        request.setFarmerId(farmer.getId());
        request.setOrchardId("orchard-" + name.replace(' ', '-').toLowerCase());
        request.setOrchard(new OrchardSnapshot(request.getOrchardId(), name, new GeoPoint(10.76, 34.80),
                "SAKIET_EZZIT", "CHEMLALI", 2, 2100, 3.8));
        request.setType(HarvestType.PLANNED);
        request.setDesiredFrom(LocalDate.of(2026, 11, 24));
        request.setDesiredTo(LocalDate.of(2026, 11, 27));
        request.setEstimatedQuantityKg(estimatedKg);
        request.setCreatedAt(Instant.parse("2026-11-20T09:00:00Z"));
        request.transitionTo(HarvestRequestStatus.SUBMITTED, farmer.getId(), null);
        request.transitionTo(HarvestRequestStatus.VALIDATED, manager.getId(), null);
        request.setValidatedAt(Instant.parse("2026-11-21T09:00:00Z"));
        return requestRepository.save(request);
    }

    private RoundStop stopFor(HarvestRequest request) {
        return RoundStop.from(request, 1, LocalTime.of(7, 30));
    }

    private Worker saveWorker(String cin) {
        Worker worker = new Worker();
        worker.setFirstName("Sami");
        worker.setLastName("Trabelsi");
        worker.setCin(cin);
        worker.setDailyRate(35);
        return workerRepository.save(worker);
    }

    private Bin saveBin(String code) {
        Bin bin = new Bin();
        bin.setCode(code);
        bin.setCapacityKg(4000);
        return (Bin) equipmentRepository.save(bin);
    }

    private Mill saveMill(String name, double capacity) {
        Mill mill = new Mill();
        mill.setName(name);
        mill.setLocation(new GeoPoint(10.77, 34.81));
        mill.setDailyCapacityKg(capacity);
        mill.setAvailableCapacityKg(capacity);
        return millRepository.save(mill);
    }

    private User saveUser(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("secret123"));
        user.setFirstName("Test");
        user.setLastName(role.name());
        user.setRoles(EnumSet.of(role));
        return userRepository.save(user);
    }

    private String token(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk()).andReturn();
        return "Bearer " + JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }
}
