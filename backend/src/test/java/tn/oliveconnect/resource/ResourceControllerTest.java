package tn.oliveconnect.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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

class ResourceControllerTest extends AbstractIntegrationTest {

    static final String PASSWORD = "secret123";

    static final String WORKER_JSON = """
            {"firstName":"Salah","lastName":"Trabelsi","cin":"%s","phone":"+21698765432",
             "skills":["picking","driving"],"dailyRate":35.5}
            """;
    static final String TRACTOR_JSON = """
            {"type":"TRACTOR","code":"%s","plateNumber":"123 TU 4567","powerHp":90}
            """;
    static final String BIN_JSON = """
            {"type":"BIN","code":"%s","serialNumber":"BN-2019-07","capacityKg":4000}
            """;
    static final String SHAKER_JSON = """
            {"type":"SHAKER","code":"%s","stockQuantity":12}
            """;
    static final String PERIOD_JSON = """
            {"from":"2026-11-20T00:00:00Z","to":"2026-11-25T00:00:00Z","reason":"Welding repair"}
            """;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    WorkerRepository workerRepository;
    @Autowired
    EquipmentRepository equipmentRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void clean() {
        workerRepository.deleteAll();
        equipmentRepository.deleteAll();
    }

    private String tokenFor(Role role) throws Exception {
        String email = role.name().toLowerCase() + "@resource-test.tn";
        if (userRepository.findByEmail(email).isEmpty()) {
            User user = new User();
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(PASSWORD));
            user.setFirstName("Test");
            user.setLastName(role.name());
            user.setRoles(EnumSet.of(role));
            userRepository.save(user);
        }
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return "Bearer " + JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String logistics() throws Exception {
        return tokenFor(Role.LOGISTICS_MANAGER);
    }

    private String create(String path, String json) throws Exception {
        MvcResult result = mockMvc.perform(post(path).header("Authorization", logistics())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    @Test
    @DisplayName("T-RS-3 logistics manager creates, reads, updates and deletes a worker")
    void workerCrud() throws Exception {
        mockMvc.perform(post("/api/workers").header("Authorization", logistics())
                .contentType(MediaType.APPLICATION_JSON).content(WORKER_JSON.formatted("12345678")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/workers/")))
                .andExpect(jsonPath("$.cin").value("12345678"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.skills.length()").value(2))
                .andExpect(jsonPath("$.unavailabilities.length()").value(0));
        String id = workerRepository.findByCin("12345678").orElseThrow().getId();

        mockMvc.perform(get("/api/workers/" + id).header("Authorization", logistics()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lastName").value("Trabelsi"));

        mockMvc.perform(put("/api/workers/" + id).header("Authorization", logistics())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"firstName":"Salah","lastName":"Trabelsi","cin":"12345678","skills":["picking"],
                         "dailyRate":40,"active":false}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyRate").value(40.0))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/workers?active=false").header("Authorization", logistics()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/workers?active=true").header("Authorization", logistics()))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(delete("/api/workers/" + id).header("Authorization", logistics()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/workers/" + id).header("Authorization", logistics()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("T-RS-4 worker with an existing cin")
    void duplicateCin() throws Exception {
        create("/api/workers", WORKER_JSON.formatted("12345678"));
        mockMvc.perform(post("/api/workers").header("Authorization", logistics())
                .contentType(MediaType.APPLICATION_JSON).content(WORKER_JSON.formatted("12345678")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CIN_TAKEN"));
    }

    @Test
    @DisplayName("T-RS-5 GET /equipment returns tractors, bins and tools, each with its own fields")
    void equipmentIsPolymorphic() throws Exception {
        create("/api/equipment", TRACTOR_JSON.formatted("TR-02"));
        create("/api/equipment", BIN_JSON.formatted("B-07"));
        create("/api/equipment", SHAKER_JSON.formatted("VIB-STOCK"));

        mockMvc.perform(get("/api/equipment").header("Authorization", logistics()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                // sorted by code: B-07, TR-02, VIB-STOCK
                .andExpect(jsonPath("$.content[0].type").value("BIN"))
                .andExpect(jsonPath("$.content[0].capacityKg").value(4000.0))
                .andExpect(jsonPath("$.content[0].serialNumber").value("BN-2019-07"))
                .andExpect(jsonPath("$.content[0].powerHp").doesNotExist())
                .andExpect(jsonPath("$.content[1].type").value("TRACTOR"))
                .andExpect(jsonPath("$.content[1].plateNumber").value("123 TU 4567"))
                .andExpect(jsonPath("$.content[1].powerHp").value(90))
                .andExpect(jsonPath("$.content[2].type").value("SHAKER"))
                .andExpect(jsonPath("$.content[2].toolType").value("SHAKER"))
                .andExpect(jsonPath("$.content[2].stockQuantity").value(12));
        assertThat(equipmentRepository.findByCode("TR-02").orElseThrow()).isInstanceOf(Tractor.class);

        mockMvc.perform(get("/api/equipment?type=BIN").header("Authorization", logistics()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("T-RS-6 equipment update, status filter and delete")
    void equipmentCrud() throws Exception {
        String id = create("/api/equipment", BIN_JSON.formatted("B-07"));

        mockMvc.perform(put("/api/equipment/" + id).header("Authorization", logistics())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"BIN\",\"code\":\"B-07\",\"status\":\"MAINTENANCE\",\"capacityKg\":4500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MAINTENANCE"))
                .andExpect(jsonPath("$.capacityKg").value(4500.0));

        mockMvc.perform(get("/api/equipment?status=MAINTENANCE").header("Authorization", logistics()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/equipment?status=AVAILABLE").header("Authorization", logistics()))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(delete("/api/equipment/" + id).header("Authorization", logistics()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/equipment/" + id).header("Authorization", logistics()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("T-RS-7 equipment with an existing code")
    void duplicateCode() throws Exception {
        create("/api/equipment", BIN_JSON.formatted("B-07"));
        mockMvc.perform(post("/api/equipment").header("Authorization", logistics())
                .contentType(MediaType.APPLICATION_JSON).content(TRACTOR_JSON.formatted("B-07")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CODE_TAKEN"));
    }

    @Test
    @DisplayName("T-RS-8 the type of an existing equipment cannot change")
    void typeCannotChange() throws Exception {
        String id = create("/api/equipment", BIN_JSON.formatted("B-07"));
        mockMvc.perform(put("/api/equipment/" + id).header("Authorization", logistics())
                .contentType(MediaType.APPLICATION_JSON).content(TRACTOR_JSON.formatted("B-07")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EQUIPMENT_TYPE_IMMUTABLE"));
    }

    @Test
    @DisplayName("T-RS-9 invalid numbers and missing fields of the type")
    void equipmentValidation() throws Exception {
        String[] bad = {
                "{\"type\":\"BIN\",\"code\":\"B-1\",\"capacityKg\":0}",
                "{\"type\":\"TRACTOR\",\"code\":\"T-1\",\"plateNumber\":\"1 TU 1\",\"powerHp\":-5}",
                "{\"type\":\"SHAKER\",\"code\":\"S-1\",\"stockQuantity\":-1}",
                "{\"type\":\"BIN\",\"code\":\"B-1\"}",
                "{\"type\":\"TRACTOR\",\"code\":\"T-1\",\"powerHp\":90}",
        };
        for (String json : bad) {
            mockMvc.perform(post("/api/equipment").header("Authorization", logistics())
                    .contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        mockMvc.perform(post("/api/workers").header("Authorization", logistics())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"A\",\"lastName\":\"B\",\"cin\":\"12345678\",\"dailyRate\":-1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("dailyRate"));
    }

    @Test
    @DisplayName("T-RS-10 unavailability is appended and returns the updated resource")
    void unavailabilityNominal() throws Exception {
        String binId = create("/api/equipment", BIN_JSON.formatted("B-07"));
        mockMvc.perform(post("/api/equipment/" + binId + "/unavailabilities").header("Authorization", logistics())
                .contentType(MediaType.APPLICATION_JSON).content(PERIOD_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("B-07"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))   // the status is not changed
                .andExpect(jsonPath("$.unavailabilities.length()").value(1))
                .andExpect(jsonPath("$.unavailabilities[0].reason").value("Welding repair"));

        String workerId = create("/api/workers", WORKER_JSON.formatted("12345678"));
        mockMvc.perform(post("/api/workers/" + workerId + "/unavailabilities").header("Authorization", logistics())
                .contentType(MediaType.APPLICATION_JSON).content(PERIOD_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unavailabilities.length()").value(1));
    }

    @Test
    @DisplayName("T-RS-11 unavailability whose end is not after its start")
    void unavailabilityInvalidRange() throws Exception {
        String workerId = create("/api/workers", WORKER_JSON.formatted("12345678"));
        mockMvc.perform(post("/api/workers/" + workerId + "/unavailabilities").header("Authorization", logistics())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"from\":\"2026-11-25T00:00:00Z\",\"to\":\"2026-11-20T00:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("to"));
        assertThat(workerRepository.findById(workerId).orElseThrow().getUnavailabilities()).isEmpty();
    }

    @Test
    @DisplayName("T-RS-12 collection manager reads but cannot write")
    void collectionManagerReadOnly() throws Exception {
        String id = create("/api/equipment", BIN_JSON.formatted("B-07"));
        String cm = tokenFor(Role.COLLECTION_MANAGER);

        mockMvc.perform(get("/api/equipment").header("Authorization", cm)).andExpect(status().isOk());
        mockMvc.perform(get("/api/equipment/" + id).header("Authorization", cm)).andExpect(status().isOk());
        mockMvc.perform(get("/api/workers").header("Authorization", cm)).andExpect(status().isOk());
        mockMvc.perform(post("/api/equipment").header("Authorization", cm)
                .contentType(MediaType.APPLICATION_JSON).content(BIN_JSON.formatted("B-08")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/equipment/" + id).header("Authorization", cm))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/equipment/" + id + "/unavailabilities").header("Authorization", cm)
                .contentType(MediaType.APPLICATION_JSON).content(PERIOD_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T-RS-13 farmer gets 403 on workers and equipment, admin has full access")
    void farmerForbiddenAdminAllowed() throws Exception {
        String farmer = tokenFor(Role.FARMER);
        mockMvc.perform(get("/api/workers").header("Authorization", farmer))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/equipment").header("Authorization", farmer)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/workers").header("Authorization", farmer)
                .contentType(MediaType.APPLICATION_JSON).content(WORKER_JSON.formatted("12345678")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/equipment").header("Authorization", tokenFor(Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(BIN_JSON.formatted("B-07")))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/equipment")).andExpect(status().isUnauthorized());
    }
}
