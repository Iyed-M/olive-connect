package tn.oliveconnect.resource;

import static org.assertj.core.api.Assertions.assertThat;
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

class SupplyControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired EquipmentRepository equipmentRepository;
    @Autowired SupplyOrderRepository orderRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void clean() {
        equipmentRepository.deleteAll();
        orderRepository.deleteAll();
    }

    private String tokenFor(Role role) throws Exception {
        String email = role.name().toLowerCase() + "@supply-test.tn";
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

    private Tool saveTool(String code, int stock) {
        Tool tool = new Tool(ToolType.NET);
        tool.setCode(code);
        tool.setStockQuantity(stock);
        return equipmentRepository.save(tool);
    }

    private String order(String toolId, int quantity) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/supply-orders").header("Authorization", tokenFor(Role.LOGISTICS_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toolId\":\"" + toolId + "\",\"quantity\":" + quantity + ",\"supplier\":\"Sfax Nets\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/supply-orders/")))
                .andExpect(jsonPath("$.status").value("ORDERED"))
                .andExpect(jsonPath("$.toolCode").value("NET-1"))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    @Test
    @DisplayName("T-RS-2 receiving a supply order adds its quantity to the stock")
    void receiveIncrementsStock() throws Exception {
        Tool net = saveTool("NET-1", 5);
        String id = order(net.getId(), 20);

        mockMvc.perform(post("/api/supply-orders/" + id + "/receive").header("Authorization", tokenFor(Role.LOGISTICS_MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.receivedAt").exists());

        Tool reloaded = (Tool) equipmentRepository.findById(net.getId()).orElseThrow();
        assertThat(reloaded.getStockQuantity()).isEqualTo(25);

        mockMvc.perform(get("/api/supply-orders?status=RECEIVED").header("Authorization", tokenFor(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("T-RS-2 receiving twice gives 409 and the stock moves once")
    void receiveTwice() throws Exception {
        Tool net = saveTool("NET-1", 5);
        String id = order(net.getId(), 20);
        String token = tokenFor(Role.LOGISTICS_MANAGER);
        mockMvc.perform(post("/api/supply-orders/" + id + "/receive").header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/supply-orders/" + id + "/receive").header("Authorization", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUPPLY_ALREADY_RECEIVED"));
        assertThat(((Tool) equipmentRepository.findById(net.getId()).orElseThrow()).getStockQuantity()).isEqualTo(25);
    }

    @Test
    @DisplayName("T-RS-2 error cases: unknown order, unknown tool, not a tool, bad quantity")
    void errors() throws Exception {
        String token = tokenFor(Role.LOGISTICS_MANAGER);
        mockMvc.perform(post("/api/supply-orders/nope/receive").header("Authorization", token))
                .andExpect(status().isNotFound());

        Bin bin = new Bin();
        bin.setCode("BIN-1");
        bin.setCapacityKg(1000);
        bin = equipmentRepository.save(bin);

        mockMvc.perform(post("/api/supply-orders").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toolId\":\"nope\",\"quantity\":3,\"supplier\":\"X\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/supply-orders").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toolId\":\"" + bin.getId() + "\",\"quantity\":3,\"supplier\":\"X\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NOT_A_TOOL"));
        mockMvc.perform(post("/api/supply-orders").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toolId\":\"" + bin.getId() + "\",\"quantity\":0,\"supplier\":\"X\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("T-RS-2 roles: admin reads only, collection manager and farmer are refused, no token is 401")
    void roles() throws Exception {
        Tool net = saveTool("NET-1", 5);
        String id = order(net.getId(), 20);

        mockMvc.perform(post("/api/supply-orders/" + id + "/receive").header("Authorization", tokenFor(Role.ADMIN)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/supply-orders").header("Authorization", tokenFor(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toolId\":\"" + net.getId() + "\",\"quantity\":1,\"supplier\":\"X\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/supply-orders").header("Authorization", tokenFor(Role.COLLECTION_MANAGER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/supply-orders").header("Authorization", tokenFor(Role.FARMER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/supply-orders")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("FR-6.5 an equipment update by staff is recorded as a correction")
    void equipmentCorrectionIsRecorded() throws Exception {
        Tool net = saveTool("NET-1", 5);
        mockMvc.perform(put("/api/equipment/" + net.getId()).header("Authorization", tokenFor(Role.LOGISTICS_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"NET\",\"code\":\"NET-1\",\"stockQuantity\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(7))
                .andExpect(jsonPath("$.corrections.length()").value(1));
    }
}
