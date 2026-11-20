package tn.oliveconnect.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tn.oliveconnect.AbstractIntegrationTest;
import tn.oliveconnect.user.AdminSeeder;
import tn.oliveconnect.user.Role;
import tn.oliveconnect.user.User;
import tn.oliveconnect.user.UserRepository;

class ImportApiTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    MongoTemplate mongoTemplate;
    @Autowired
    ImportJobRepository jobRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    AdminSeeder adminSeeder;
    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void clean() throws IOException {
        jobRepository.deleteAll();
        mongoTemplate.remove(new Query(), WeatherReading.class);
        userRepository.deleteAll();
        adminSeeder.seed();
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

    /** T-IM-1 */
    @Test
    void validWeatherFileIsImported() throws Exception {
        drop("weather_202511210600.json", weatherFile(0, 10, List.of()));

        mockMvc.perform(post("/api/imports/run").param("type", "DYNAMIC").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs.length()").value(1))
                .andExpect(jsonPath("$.jobs[0].dataset").value("WEATHER"))
                .andExpect(jsonPath("$.jobs[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.jobs[0].totalRows").value(10))
                .andExpect(jsonPath("$.jobs[0].insertedRows").value(10))
                .andExpect(jsonPath("$.jobs[0].rejectedRows").value(0));

        assertThat(mongoTemplate.count(new Query(), WeatherReading.class)).isEqualTo(10);
        WeatherReading stored = mongoTemplate.findOne(new Query(), WeatherReading.class);
        assertThat(stored.meta().delegation()).isEqualTo("SAKIET_EZZIT");   // "Sakiet Ezzit" in the file
        assertThat(INBOX.resolve("processed/weather_202511210600.json")).exists();
        assertThat(INBOX.resolve("weather_202511210600.json")).doesNotExist();
    }

    /** T-IM-2 */
    @Test
    void invalidLinesAreRejectedWithTheirPosition() throws Exception {
        drop("weather_202511210615.json", weatherFile(0, 10, List.of(2, 5, 8)));

        MvcResult result = mockMvc.perform(post("/api/imports/run").param("type", "DYNAMIC")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs[0].status").value("PARTIAL"))
                .andExpect(jsonPath("$.jobs[0].insertedRows").value(7))
                .andExpect(jsonPath("$.jobs[0].rejectedRows").value(3))
                .andReturn();
        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.jobs[0].id");

        mockMvc.perform(get("/api/imports/" + id).header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorCount").value(3))
                .andExpect(jsonPath("$.errors[0].line").value(2))
                .andExpect(jsonPath("$.errors[0].field").value("humidityPct"))
                .andExpect(jsonPath("$.errors[1].line").value(5))
                .andExpect(jsonPath("$.errors[1].field").value("rainfallMm"))
                .andExpect(jsonPath("$.errors[2].line").value(8))
                .andExpect(jsonPath("$.errors[2].reason").value("'abc' is not a number"));
        assertThat(mongoTemplate.count(new Query(), WeatherReading.class)).isEqualTo(7);
    }

    /** T-IM-3 */
    @Test
    void sameFileTwiceIsSkipped() throws Exception {
        String content = weatherFile(0, 4, List.of());
        drop("weather_202511210630.json", content);
        mockMvc.perform(post("/api/imports/run").param("type", "DYNAMIC").header("Authorization", adminToken()))
                .andExpect(jsonPath("$.jobs.length()").value(1));

        drop("weather_202511210630.json", content);
        mockMvc.perform(post("/api/imports/run").param("type", "DYNAMIC").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs.length()").value(0))
                .andExpect(jsonPath("$.skippedFiles").value(1));

        assertThat(mongoTemplate.count(new Query(), WeatherReading.class)).isEqualTo(4);
        assertThat(jobRepository.count()).isEqualTo(1);
        assertThat(INBOX.resolve("weather_202511210630.json")).doesNotExist();
    }

    /** T-IM-5 */
    @Test
    void moreThanHalfInvalidFailsAndCommitsNothing() throws Exception {
        drop("weather_202511210645.json", weatherFile(0, 6, List.of(1, 2, 3, 4)));

        mockMvc.perform(post("/api/imports/run").param("type", "DYNAMIC").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs[0].status").value("FAILED"))
                .andExpect(jsonPath("$.jobs[0].rejectedRows").value(4))
                .andExpect(jsonPath("$.jobs[0].insertedRows").value(0));

        assertThat(mongoTemplate.count(new Query(), WeatherReading.class)).isZero();
        assertThat(INBOX.resolve("rejected/weather_202511210645.json")).exists();
    }

    @Test
    void brokenFileFailsWithFileFormatError() throws Exception {
        drop("weather_202511210700.json", "{ \"readings\": [ { \"stationId\": ");

        mockMvc.perform(post("/api/imports/run").param("type", "DYNAMIC").header("Authorization", adminToken()))
                .andExpect(jsonPath("$.jobs[0].status").value("FAILED"))
                .andExpect(jsonPath("$.jobs[0].errors[0].reason").value(org.hamcrest.Matchers.startsWith("FILE_FORMAT")));
        assertThat(INBOX.resolve("rejected/weather_202511210700.json")).exists();
    }

    @Test
    void readingAlreadyStoredIsRejectedAsDuplicate() throws Exception {
        drop("weather_202511210715.json", weatherFile(0, 4, List.of()));
        mockMvc.perform(post("/api/imports/run").param("type", "DYNAMIC").header("Authorization", adminToken()));

        // another file, same station and timestamps for the first reading, three new ones
        drop("weather_202511210730.json", weatherFile(3, 4, List.of()));
        mockMvc.perform(post("/api/imports/run").param("type", "DYNAMIC").header("Authorization", adminToken()))
                .andExpect(jsonPath("$.jobs[0].status").value("PARTIAL"))
                .andExpect(jsonPath("$.jobs[0].insertedRows").value(3))
                .andExpect(jsonPath("$.jobs[0].errors[0].reason").value("duplicate reading"));
        assertThat(mongoTemplate.count(new Query(), WeatherReading.class)).isEqualTo(7);
    }

    @Test
    void uploadImportsTheFileAndJournalCanBeFiltered() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "weather_202511210800.json", "application/json",
                weatherFile(0, 3, List.of()).getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/imports/upload").file(file).header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs[0].status").value("SUCCESS"));

        mockMvc.perform(get("/api/imports").param("dataset", "WEATHER").param("status", "SUCCESS")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].fileName").value("weather_202511210800.json"))
                .andExpect(jsonPath("$.content[0].errors.length()").value(0));
        mockMvc.perform(get("/api/imports").param("status", "FAILED").header("Authorization", adminToken()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void uploadWithUnknownFileNameIsRefused() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/api/imports/upload").file(file).header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void onlyAdminCanUseTheImportApi() throws Exception {
        User farmer = new User();
        farmer.setEmail("ali@example.tn");
        farmer.setPasswordHash(passwordEncoder.encode("secret123"));
        farmer.setFirstName("Ali");
        farmer.setLastName("Ben Ali");
        farmer.setRoles(EnumSet.of(Role.COLLECTION_MANAGER));
        userRepository.save(farmer);
        String token = login("ali@example.tn", "secret123");

        mockMvc.perform(get("/api/imports").header("Authorization", token)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/imports/run").param("type", "DYNAMIC").header("Authorization", token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/imports")).andExpect(status().isUnauthorized());
    }

    @Test
    void weatherReadingsIsATimeSeriesCollection() {
        var info = mongoTemplate.executeCommand("{ listCollections: 1, filter: { name: 'weather_readings' } }");
        assertThat(info.toJson()).contains("timeseries").contains("\"metaField\": \"meta\"");
    }

    @Test
    void unknownJobIsNotFound() throws Exception {
        mockMvc.perform(get("/api/imports/000000000000000000000000").header("Authorization", adminToken()))
                .andExpect(status().isNotFound());
    }

    /** A weather file with `count` readings starting at hour `firstHour`; the 1-based positions in `bad` are invalid. */
    private String weatherFile(int firstHour, int count, List<Integer> bad) {
        String readings = IntStream.range(0, count).mapToObj(i -> {
            int position = i + 1;
            String humidity = "81";
            String rainfall = "2.4";
            String temperature = "14.2";
            if (bad.contains(position)) {
                switch (bad.indexOf(position) % 3) {
                    case 0 -> humidity = "150";
                    case 1 -> rainfall = "-1";
                    default -> temperature = "\"abc\"";
                }
            }
            return """
                    { "stationId": "SFX-03", "delegation": "Sakiet Ezzit", "timestamp": "2025-11-21T%02d:00:00Z",
                      "temperatureC": %s, "humidityPct": %s, "rainfallMm": %s, "windKmh": 12 }"""
                    .formatted(firstHour + i, temperature, humidity, rainfall);
        }).collect(Collectors.joining(",\n"));
        return "{ \"generatedAt\": \"2025-11-21T06:15:00Z\", \"source\": \"SIM-WEATHER\", \"readings\": [\n" + readings + "\n] }";
    }

    private void drop(String fileName, String content) throws IOException {
        Files.writeString(INBOX.resolve(fileName), content);
    }

    private String adminToken() throws Exception {
        return login("admin@oliveconnect.tn", "admin1234");
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return "Bearer " + JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }
}
