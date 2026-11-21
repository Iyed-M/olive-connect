package tn.oliveconnect.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tn.oliveconnect.AbstractIntegrationTest;
import tn.oliveconnect.mill.MillReception;
import tn.oliveconnect.user.AdminSeeder;

/** Historical reception facts are imported through the same public import API as other dynamic datasets. */
class HistoricalReceptionImportApiTest extends AbstractIntegrationTest {

    private static final String HEADER =
            "millId;roundId;binId;orchardId;farmerId;harvestRequestId;grossWeightKg;tareKg;netWeightKg;"
                    + "binCapacityKg;fillRate;qualityGrade;defectRatePct;leafRatePct;estimatedOilYieldPct;season;"
                    + "varietyCode;delegation;orchardAreaHa;requestValidatedAt;leadTimeHours;receivedAt;receivedById\n";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    MongoTemplate mongoTemplate;
    @Autowired
    ImportJobRepository jobRepository;
    @Autowired
    AdminSeeder adminSeeder;

    @BeforeEach
    @AfterEach
    void clean() throws IOException {
        jobRepository.deleteAll();
        mongoTemplate.remove(Query.query(Criteria.where("roundId").regex("^HIST-")), MillReception.class);
        for (String folder : List.of("", "processed", "rejected")) {
            Path directory = folder.isEmpty() ? INBOX : INBOX.resolve(folder);
            if (Files.isDirectory(directory)) {
                try (var files = Files.list(directory)) {
                    for (Path file : files.filter(Files::isRegularFile).toList()) {
                        Files.delete(file);
                    }
                }
            }
        }
    }

    @Test
    void historicalReceptionFactsAreBulkInsertedWithTheirDocumentDimensions() throws Exception {
        String content = HEADER
                + reception("MIL-SFX-01", "HIST-2023-0001", "BIN-0001", "ORC-0001", 4350, 820, 3530, 4000,
                        0.8825, "GOOD", "2023-2024", "CHEMLALI", "SAKIET_EZZIT", 5.7,
                        "2023-11-21T06:00:00Z", 97.5, "2023-11-25T07:30:00Z")
                + reception("MIL-SFX-02", "HIST-2023-0002", "BIN-0002", "ORC-0002", 3700, 700, 3000, 4000,
                        0.75, "AVERAGE", "2023-2024", "CHETOUI", "AGAREB", 3.2,
                        "2023-12-01T08:00:00Z", 24, "2023-12-02T08:00:00Z");
        drop("receptions_20231231.csv", content);

        run().andExpect(jsonPath("$.jobs[0].dataset").value("RECEPTIONS"))
                .andExpect(jsonPath("$.jobs[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.jobs[0].insertedRows").value(2));

        List<MillReception> receptions = mongoTemplate.find(
                Query.query(Criteria.where("roundId").regex("^HIST-")), MillReception.class);
        assertThat(receptions).hasSize(2);
        MillReception first = receptions.stream().filter(item -> item.getRoundId().equals("HIST-2023-0001")).findFirst()
                .orElseThrow();
        assertThat(first.getMillId()).isEqualTo("MIL-SFX-01");
        assertThat(first.getOrchardId()).isEqualTo("ORC-0001");
        assertThat(first.getNetWeightKg()).isEqualTo(3530);
        assertThat(first.getFillRate()).isEqualTo(0.8825);
        assertThat(first.getQuality().getGrade().name()).isEqualTo("GOOD");
        assertThat(first.getSeason()).isEqualTo("2023-2024");
        assertThat(first.getVarietyCode()).isEqualTo("CHEMLALI");
        assertThat(first.getDelegation()).isEqualTo("SAKIET_EZZIT");
        assertThat(first.getOrchardAreaHa()).isEqualTo(5.7);
        assertThat(first.getLeadTimeHours()).isEqualTo(97.5);
    }

    @Test
    void invalidAndDuplicateFactsAreRejectedButARepeatedFileIsSkippedByChecksum() throws Exception {
        String content = HEADER
                + reception("MIL-SFX-01", "HIST-2024-0001", "BIN-0001", "ORC-0001", 4350, 820, 3530, 4000,
                        0.8825, "GOOD", "2024-2025", "CHEMLALI", "SAKIET_EZZIT", 5.7,
                        "2024-11-21T06:00:00Z", 24, "2024-11-22T06:00:00Z")
                + reception("MIL-SFX-01", "HIST-2024-0001", "BIN-0001", "ORC-0001", 4350, 820, 3530, 4000,
                        0.8825, "GOOD", "2024-2025", "CHEMLALI", "SAKIET_EZZIT", 5.7,
                        "2024-11-21T06:00:00Z", 24, "2024-11-22T06:00:00Z")
                + reception("MIL-SFX-02", "HIST-2024-0002", "BIN-0002", "ORC-0002", 3700, 700, 3000, 4000,
                        0.75, "AVERAGE", "2024-2025", "CHETOUI", "AGAREB", 3.2,
                        "2024-12-01T08:00:00Z", 24, "2024-12-02T08:00:00Z")
                + reception("MIL-SFX-02", "HIST-2024-0003", "BIN-0003", "ORC-0003", -1, 700, 3000, 4000,
                        0.75, "AVERAGE", "2024-2025", "CHETOUI", "AGAREB", 3.2,
                        "2024-12-01T08:00:00Z", 24, "2024-12-02T08:00:00Z");
        drop("receptions_20241231.csv", content);

        run().andExpect(jsonPath("$.jobs[0].status").value("PARTIAL"))
                .andExpect(jsonPath("$.jobs[0].totalRows").value(4))
                .andExpect(jsonPath("$.jobs[0].rejectedRows").value(2))
                .andExpect(jsonPath("$.jobs[0].errors[0].reason").value("duplicate reception key"))
                .andExpect(jsonPath("$.jobs[0].errors[1].field").value("grossWeightKg"));
        assertThat(mongoTemplate.count(Query.query(Criteria.where("roundId").regex("^HIST-")), MillReception.class))
                .isEqualTo(2);

        drop("receptions_20241231.csv", content);
        run().andExpect(jsonPath("$.skippedFiles").value(1));
        assertThat(mongoTemplate.count(Query.query(Criteria.where("roundId").regex("^HIST-")), MillReception.class))
                .isEqualTo(2);
    }

    private String reception(String millId, String roundId, String binId, String orchardId, double gross,
                             double tare, double net, double capacity, double fill, String grade, String season,
                             String variety, String delegation, double area, String validatedAt, double leadTime,
                             String receivedAt) {
        return String.join(";", millId, roundId, binId, orchardId, "FARMER-1", "REQUEST-" + roundId,
                Double.toString(gross), Double.toString(tare), Double.toString(net), Double.toString(capacity),
                Double.toString(fill), grade, "3.1", "1.2", "21.0", season, variety, delegation,
                Double.toString(area), validatedAt, Double.toString(leadTime), receivedAt, "MILL-MANAGER-1") + "\n";
    }

    private ResultActions run() throws Exception {
        return mockMvc.perform(post("/api/imports/run").param("type", "DYNAMIC")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk());
    }

    private void drop(String fileName, String content) throws IOException {
        Files.writeString(INBOX.resolve(fileName), content);
    }

    private String adminToken() throws Exception {
        adminSeeder.seed();
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@oliveconnect.tn\",\"password\":\"admin1234\"}"))
                .andExpect(status().isOk()).andReturn();
        return "Bearer " + JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }
}
