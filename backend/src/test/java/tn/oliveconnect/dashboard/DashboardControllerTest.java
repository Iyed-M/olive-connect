package tn.oliveconnect.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tn.oliveconnect.AbstractIntegrationTest;
import tn.oliveconnect.mill.Mill;
import tn.oliveconnect.mill.MillReception;
import tn.oliveconnect.mill.MillRepository;
import tn.oliveconnect.mill.QualityAssessment;
import tn.oliveconnect.mill.QualityGrade;
import tn.oliveconnect.user.Role;
import tn.oliveconnect.user.User;
import tn.oliveconnect.user.UserRepository;

class DashboardControllerTest extends AbstractIntegrationTest {

    private static final String SEASON = "2026-2027";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    MongoTemplate mongoTemplate;
    @Autowired
    MillRepository millRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    StringRedisTemplate redis;
    @SpyBean
    KpiRepositoryCustom kpiRepository;

    @BeforeEach
    void clean() {
        mongoTemplate.remove(new Query(), MillReception.class);
        millRepository.deleteAll();
        userRepository.deleteAll();
        var keys = redis.keys("cache:kpi::*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        clearInvocations(kpiRepository);
    }

    @Test
    void collectionManagerCanReadSummaryAndExportCsv() throws Exception {
        Mill mill = saveMill("North");
        User manager = saveUser("manager@dashboard-test.tn", Role.COLLECTION_MANAGER, null);
        saveReception(mill.getId(), "orchard-1", "2026-11-16T10:00:00Z", 2_000, 4_000);
        saveReception(mill.getId(), "orchard-2", "2026-11-18T10:00:00Z", 3_600, 4_000);

        mockMvc.perform(get("/api/dashboard/summary").param("season", SEASON)
                        .header("Authorization", token(manager.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receptions").value(2))
                .andExpect(jsonPath("$.totalNetKg").value(5_600))
                .andExpect(jsonPath("$.avgYieldKgHa").value(1_400.0));

        String token = token(manager.getEmail());
        mockMvc.perform(get("/api/dashboard/yield").param("season", SEASON).param("groupBy", "orchard")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/dashboard/lead-time").param("season", SEASON).param("groupBy", "delegation")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].n").value(2));
        mockMvc.perform(get("/api/dashboard/mill-volumes").param("season", SEASON).param("millId", mill.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].totalNetKg").value(2_000))
                .andExpect(jsonPath("$[0].capacityUsePct").value(20.0));

        mockMvc.perform(get("/api/dashboard/fill-rate/export.csv")
                        .param("season", SEASON).param("groupBy", "week")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("text/csv")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("fill-rate.csv")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("avgFillRate")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("0.7")));
    }

    @Test
    void millManagerOnlySeesTheAssignedMillAndSecondReadUsesTheCache() throws Exception {
        Mill ownMill = saveMill("Own");
        Mill otherMill = saveMill("Other");
        User manager = saveUser("mill-manager@dashboard-test.tn", Role.MILL_MANAGER, ownMill.getId());
        saveReception(ownMill.getId(), "own-orchard", "2026-11-16T10:00:00Z", 2_000, 4_000);
        saveReception(otherMill.getId(), "other-orchard", "2026-11-17T10:00:00Z", 3_600, 4_000);

        String token = token(manager.getEmail());
        mockMvc.perform(get("/api/dashboard/fill-rate").param("season", SEASON)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].receptions").value(1));
        verify(kpiRepository, times(1)).aggregateFillRate(any(), eq("week"));
        mockMvc.perform(get("/api/dashboard/fill-rate").param("season", SEASON)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].receptions").value(1));

        var keys = redis.keys("cache:kpi::*");
        assertThat(keys).hasSize(1);
        assertThat(redis.getExpire(keys.iterator().next())).isBetween(1L, 300L);

        mockMvc.perform(get("/api/dashboard/mill-volumes").param("season", SEASON)
                        .param("millId", otherMill.getId()).header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @Test
    void unsupportedRoleCannotOpenTheDashboard() throws Exception {
        User farmer = saveUser("farmer@dashboard-test.tn", Role.FARMER, null);

        mockMvc.perform(get("/api/dashboard/summary").param("season", SEASON)
                        .header("Authorization", token(farmer.getEmail())))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidDashboardFiltersReturnValidationProblems() throws Exception {
        User manager = saveUser("validation@dashboard-test.tn", Role.COLLECTION_MANAGER, null);
        String token = token(manager.getEmail());

        mockMvc.perform(get("/api/dashboard/fill-rate").header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/dashboard/fill-rate").param("season", SEASON)
                        .param("delegation", "nowhere").header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/dashboard/fill-rate").param("season", SEASON)
                        .param("groupBy", "month").header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/dashboard/fill-rate").param("season", SEASON)
                        .param("from", "2026-11-20").param("to", "2026-11-19")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void millManagerCannotExportACollectionManagerKpi() throws Exception {
        Mill mill = saveMill("Assigned");
        User manager = saveUser("export-manager@dashboard-test.tn", Role.MILL_MANAGER, mill.getId());

        mockMvc.perform(get("/api/dashboard/yield/export.csv").param("season", SEASON)
                        .header("Authorization", token(manager.getEmail())))
                .andExpect(status().isForbidden());
    }

    private Mill saveMill(String name) {
        Mill mill = new Mill();
        mill.setName(name + " " + UUID.randomUUID());
        mill.setDailyCapacityKg(10_000);
        mill.setAvailableCapacityKg(10_000);
        return millRepository.save(mill);
    }

    private User saveUser(String email, Role role, String millId) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("secret123"));
        user.setFirstName(role.name());
        user.setLastName("Test");
        user.setRoles(EnumSet.of(role));
        user.setAssignedMillId(millId);
        return userRepository.save(user);
    }

    private MillReception saveReception(String millId, String orchardId, String receivedAt,
            double netWeightKg, double capacityKg) {
        MillReception reception = new MillReception();
        reception.setMillId(millId);
        reception.setRoundId(UUID.randomUUID().toString());
        reception.setBinId(UUID.randomUUID().toString());
        reception.setOrchardId(orchardId);
        reception.setFarmerId("farmer-" + orchardId);
        reception.setGrossWeightKg(netWeightKg);
        reception.setTareKg(0);
        reception.setBinCapacityKg(capacityKg);
        reception.computeNetAndFillRate();
        reception.setSeason(SEASON);
        reception.setVarietyCode("CHEMLALI");
        reception.setDelegation("SAKIET_EZZIT");
        reception.setOrchardAreaHa(2);
        reception.setLeadTimeHours(48);
        reception.setReceivedAt(Instant.parse(receivedAt));
        QualityAssessment quality = new QualityAssessment();
        quality.setGrade(QualityGrade.GOOD);
        quality.setEstimatedOilYieldPct(20);
        reception.setQuality(quality);
        return mongoTemplate.save(reception);
    }

    private String token(String email) throws Exception {
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk()).andReturn();
        return "Bearer " + JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }
}
