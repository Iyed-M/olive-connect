package tn.oliveconnect.mill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.greaterThan;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tn.oliveconnect.AbstractIntegrationTest;
import tn.oliveconnect.harvest.HarvestRequest;
import tn.oliveconnect.harvest.HarvestRequestRepository;
import tn.oliveconnect.harvest.HarvestRequestStatus;
import tn.oliveconnect.harvest.OrchardSnapshot;
import tn.oliveconnect.notification.NotificationRepository;
import tn.oliveconnect.notification.NotificationType;
import tn.oliveconnect.orchard.GeoPoint;
import tn.oliveconnect.resource.Bin;
import tn.oliveconnect.resource.EquipmentRepository;
import tn.oliveconnect.round.CollectionRound;
import tn.oliveconnect.round.CollectionRoundRepository;
import tn.oliveconnect.round.RoundStop;
import tn.oliveconnect.round.RoundStatus;
import tn.oliveconnect.round.StopStatus;
import tn.oliveconnect.user.Role;
import tn.oliveconnect.user.User;
import tn.oliveconnect.user.UserRepository;

class MillReceptionControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MillRepository millRepository;
    @Autowired MillReceptionRepository receptionRepository;
    @Autowired CollectionRoundRepository roundRepository;
    @Autowired HarvestRequestRepository requestRepository;
    @Autowired EquipmentRepository equipmentRepository;
    @Autowired UserRepository userRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired MongoTemplate mongoTemplate;

    @BeforeEach
    void clean() {
        notificationRepository.deleteAll();
        receptionRepository.deleteAll();
        roundRepository.deleteAll();
        requestRepository.deleteAll();
        equipmentRepository.deleteAll();
        millRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void managerRecordsWeighingAndFarmerSeesTheReception() throws Exception {
        Mill mill = saveMill("Sfax North", 10_000);
        User manager = saveUser("manager@mill-test.tn", Role.MILL_MANAGER, mill.getId());
        User farmer = saveUser("farmer@mill-test.tn", Role.FARMER, null);
        Bin bin = saveBin("B-07", 4_000);
        CollectionRound round = saveRound(mill, bin, farmer, "Orchard North");

        MvcResult result = mockMvc.perform(post("/api/mills/" + mill.getId() + "/receptions")
                        .header("Authorization", token(manager.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receptionJson(round, bin, round.getStops().get(0).getOrchardId(), "4350", "820")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.grossWeightKg").value(4350))
                .andExpect(jsonPath("$.tareKg").value(820))
                .andExpect(jsonPath("$.netWeightKg").value(3530))
                .andExpect(jsonPath("$.fillRate").value(0.8825))
                .andExpect(jsonPath("$.quality.grade").value("GOOD"))
                .andExpect(jsonPath("$.season").value(seasonFor(LocalDate.now(ZoneOffset.UTC))))
                .andExpect(jsonPath("$.varietyCode").value("CHEMLALI"))
                .andExpect(jsonPath("$.delegation").value("SAKIET_EZZIT"))
                .andExpect(jsonPath("$.orchardAreaHa").value(5.7))
                .andExpect(jsonPath("$.requestValidatedAt").exists())
                .andExpect(jsonPath("$.leadTimeHours").value(greaterThan(96.0)))
                .andReturn();

        String receptionId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        Mill savedMill = millRepository.findById(mill.getId()).orElseThrow();
        assertThat(savedMill.getAvailableCapacityKg()).isEqualTo(6_470);
        assertThat(receptionRepository.findById(receptionId)).isPresent();
        assertThat(notificationRepository.findAll()).anyMatch(notification ->
                notification.getRecipientId().equals(farmer.getId())
                        && notification.getType() == NotificationType.RECEPTION_RECORDED);

        mockMvc.perform(get("/api/receptions").param("mine", "true")
                        .header("Authorization", token(farmer.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(receptionId));

        User otherFarmer = saveUser("other-farmer@mill-test.tn", Role.FARMER, null);
        mockMvc.perform(get("/api/receptions").param("mine", "true")
                        .header("Authorization", token(otherFarmer.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/receptions/" + receptionId)
                        .header("Authorization", token(farmer.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(receptionId));
    }

    @Test
    void managerCanPublishCapacityAndSeeTodaysIncomingRounds() throws Exception {
        Mill mill = saveMill("Sfax North", 10_000);
        User manager = saveUser("manager@incoming-test.tn", Role.MILL_MANAGER, mill.getId());
        User farmer = saveUser("farmer@incoming-test.tn", Role.FARMER, null);
        Bin bin = saveBin("B-08", 4_000);
        CollectionRound round = saveRound(mill, bin, farmer, "Orchard Incoming");

        mockMvc.perform(put("/api/mills/" + mill.getId() + "/capacity")
                        .header("Authorization", token(manager.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"availableCapacityKg\":7500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCapacityKg").value(7500));

        mockMvc.perform(get("/api/mills/" + mill.getId() + "/incoming")
                        .header("Authorization", token(manager.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(round.getId()))
                .andExpect(jsonPath("$[0].stops[0].orchardId").value("orchard-incoming"))
                .andExpect(jsonPath("$[0].bins[0].id").value(bin.getId()))
                .andExpect(jsonPath("$[0].bins[0].capacityKg").value(4000));
    }

    @Test
    void invalidBinAndWeightsAreRejected() throws Exception {
        Mill mill = saveMill("Sfax North", 10_000);
        User manager = saveUser("manager@validation-test.tn", Role.MILL_MANAGER, mill.getId());
        User farmer = saveUser("farmer@validation-test.tn", Role.FARMER, null);
        Bin roundBin = saveBin("B-09", 4_000);
        Bin otherBin = saveBin("B-10", 4_000);
        CollectionRound round = saveRound(mill, roundBin, farmer, "Orchard Validation");

        mockMvc.perform(post("/api/mills/" + mill.getId() + "/receptions")
                        .header("Authorization", token(manager.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receptionJson(round, roundBin, "orchard-not-in-round", "4350", "820")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ORCHARD_NOT_IN_ROUND"));

        mockMvc.perform(post("/api/mills/" + mill.getId() + "/receptions")
                        .header("Authorization", token(manager.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receptionJson(round, otherBin, "orchard-validation", "4350", "820")))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post("/api/mills/" + mill.getId() + "/receptions")
                        .header("Authorization", token(manager.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receptionJson(round, roundBin, "orchard-validation", "6000", "100")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void duplicateReceptionReturnsConflict() throws Exception {
        Mill mill = saveMill("Sfax North", 10_000);
        User manager = saveUser("manager@duplicate-test.tn", Role.MILL_MANAGER, mill.getId());
        User farmer = saveUser("farmer@duplicate-test.tn", Role.FARMER, null);
        Bin bin = saveBin("B-11", 4_000);
        CollectionRound round = saveRound(mill, bin, farmer, "Orchard Duplicate");
        String body = receptionJson(round, bin, "orchard-duplicate", "4350", "820");

        mockMvc.perform(post("/api/mills/" + mill.getId() + "/receptions")
                        .header("Authorization", token(manager.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/mills/" + mill.getId() + "/receptions")
                        .header("Authorization", token(manager.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());

        assertThat(receptionRepository.count()).isEqualTo(1);
        assertThat(millRepository.findById(mill.getId()).orElseThrow().getAvailableCapacityKg()).isEqualTo(6_470);
    }

    @Test
    void managerOfAnotherMillIsForbidden() throws Exception {
        Mill targetMill = saveMill("Sfax North", 10_000);
        Mill assignedMill = saveMill("Sfax South", 10_000);
        User manager = saveUser("manager@ownership-test.tn", Role.MILL_MANAGER, assignedMill.getId());
        User farmer = saveUser("farmer@ownership-test.tn", Role.FARMER, null);
        Bin bin = saveBin("B-12", 4_000);
        CollectionRound round = saveRound(targetMill, bin, farmer, "Orchard Ownership");

        mockMvc.perform(get("/api/mills/" + targetMill.getId() + "/incoming")
                        .header("Authorization", token(manager.getEmail())))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/mills/" + targetMill.getId() + "/receptions")
                        .header("Authorization", token(manager.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receptionJson(round, bin, "orchard-ownership", "4350", "820")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListReceptions() throws Exception {
        User admin = saveUser("admin@reception-test.tn", Role.ADMIN, null);

        mockMvc.perform(get("/api/receptions")
                        .header("Authorization", token(admin.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void receptionIndexesAreDeclared() {
        assertThat(mongoTemplate.indexOps(MillReception.class).getIndexInfo())
                .extracting(index -> index.getName())
                .contains("season_received_at", "season_orchard", "mill_received_at", "farmer_received_at",
                        "round_bin_orchard_unique");
        assertThat(mongoTemplate.indexOps(CollectionRound.class).getIndexInfo())
                .extracting(index -> index.getName())
                .contains("mill_date_start_time");
    }

    private Mill saveMill(String name, double capacityKg) {
        Mill mill = new Mill();
        mill.setName(name + " " + UUID.randomUUID());
        mill.setDailyCapacityKg(capacityKg);
        mill.setAvailableCapacityKg(capacityKg);
        return millRepository.save(mill);
    }

    private User saveUser(String email, Role role, String assignedMillId) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("secret123"));
        user.setFirstName(role.name());
        user.setLastName("Test");
        user.setRoles(EnumSet.of(role));
        user.setAssignedMillId(assignedMillId);
        return userRepository.save(user);
    }

    private Bin saveBin(String code, double capacityKg) {
        Bin bin = new Bin();
        bin.setCode(code);
        bin.setCapacityKg(capacityKg);
        return (Bin) equipmentRepository.save(bin);
    }

    private CollectionRound saveRound(Mill mill, Bin bin, User farmer, String orchardName) {
        LocalDate date = LocalDate.now(ZoneOffset.UTC);
        String orchardId = orchardName.toLowerCase().replace(' ', '-');
        HarvestRequest request = new HarvestRequest();
        request.setFarmerId(farmer.getId());
        request.setOrchardId(orchardId);
        request.setOrchard(new OrchardSnapshot(orchardId, orchardName,
                new GeoPoint(10.76, 34.80), "SAKIET_EZZIT", "CHEMLALI", 5.7, 2100, 3.8));
        request.setSeason(seasonFor(date));
        request.setStatus(HarvestRequestStatus.VALIDATED);
        request.setValidatedAt(Instant.now().minusSeconds(97 * 3600));
        request = requestRepository.save(request);

        RoundStop stop = new RoundStop();
        stop.setOrder(1);
        stop.setHarvestRequestId(request.getId());
        stop.setOrchardId(orchardId);
        stop.setFarmerId(farmer.getId());
        stop.setOrchardName(orchardName);
        stop.setStatus(StopStatus.DONE);
        stop.setHarvestedKg(3500.0);

        CollectionRound round = new CollectionRound();
        round.setCode("T-" + UUID.randomUUID());
        round.setDate(date);
        round.setStartTime(LocalTime.of(7, 0));
        round.setEndTime(LocalTime.of(15, 0));
        round.setStatus(RoundStatus.COMPLETED);
        round.setMillId(mill.getId());
        round.setEquipmentIds(List.of(bin.getId()));
        round.setStops(List.of(stop));
        round.setSeason(seasonFor(date));
        return roundRepository.save(round);
    }

    private String receptionJson(CollectionRound round, Bin bin, String orchardId, String gross, String tare) {
        return "{\"roundId\":\"" + round.getId() + "\",\"binId\":\"" + bin.getId()
                + "\",\"orchardId\":\"" + orchardId + "\",\"grossWeightKg\":" + gross
                + ",\"tareKg\":" + tare
                + ",\"quality\":{\"grade\":\"GOOD\",\"defectRatePct\":3.1,"
                + "\"leafRatePct\":1.2,\"estimatedOilYieldPct\":21}}";
    }

    private String token(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk()).andReturn();
        return "Bearer " + JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String seasonFor(LocalDate date) {
        int startYear = date.getMonthValue() >= 7 ? date.getYear() : date.getYear() - 1;
        return startYear + "-" + (startYear + 1);
    }
}
