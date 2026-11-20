package tn.oliveconnect.orchard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tn.oliveconnect.AbstractIntegrationTest;
import tn.oliveconnect.auth.AuthService;
import tn.oliveconnect.user.AdminSeeder;
import tn.oliveconnect.user.Delegation;
import tn.oliveconnect.user.FarmerProfile;
import tn.oliveconnect.user.Role;
import tn.oliveconnect.user.User;
import tn.oliveconnect.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

class OrchardControllerTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    OrchardRepository orchardRepository;
    @Autowired
    OliveVarietyRepository varietyRepository;
    @Autowired
    OliveVarietySeeder varietySeeder;
    @Autowired
    AdminSeeder adminSeeder;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    MongoTemplate mongoTemplate;
    @Autowired
    StringRedisTemplate redis;

    @BeforeEach
    void clean() {
        orchardRepository.deleteAll();
        varietyRepository.deleteAll();
        userRepository.deleteAll();
        adminSeeder.seed();
        varietySeeder.seed();
    }

    @Test
    void farmerCreatesOrchardAndTotalsAreRecomputed() throws Exception {
        createFarmer("ali@example.tn", "M-001");

        mockMvc.perform(post("/api/orchards").header("Authorization", farmerToken("ali@example.tn"))
                        .contentType(MediaType.APPLICATION_JSON).content(orchardJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.areaHa").value(5.7))
                .andExpect(jsonPath("$.treeCount").value(630))
                .andExpect(jsonPath("$.mainVarietyCode").value("CHEMLALI"))
                .andExpect(jsonPath("$.owner.displayName").value("Ali Ben Ali"));
    }

    @Test
    void anotherFarmerCannotReadTheOrchard() throws Exception {
        createFarmer("ali@example.tn", "M-001");
        String orchardId = createOrchard("ali@example.tn");
        createFarmer("other@example.tn", "M-002");

        mockMvc.perform(get("/api/orchards/" + orchardId)
                        .header("Authorization", farmerToken("other@example.tn")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void invalidPlotReturns400() throws Exception {
        createFarmer("ali@example.tn", "M-001");
        String invalid = orchardJson().replace("\"areaHa\":4.5", "\"areaHa\":0");

        mockMvc.perform(post("/api/orchards").header("Authorization", farmerToken("ali@example.tn"))
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void coordinatesOutsideTunisiaReturn400() throws Exception {
        createFarmer("ali@example.tn", "M-001");
        String invalid = orchardJson().replace("10.7603,34.7998", "0,0");

        mockMvc.perform(post("/api/orchards").header("Authorization", farmerToken("ali@example.tn"))
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("location.coordinates"));
    }

    @Test
    void collectionManagerCanReadAllOrchards() throws Exception {
        createFarmer("ali@example.tn", "M-001");
        createFarmer("other@example.tn", "M-002");
        createOrchard("ali@example.tn");
        createOrchard("other@example.tn");
        User manager = user("manager@example.tn", EnumSet.of(Role.COLLECTION_MANAGER), null);
        userRepository.save(manager);

        mockMvc.perform(get("/api/orchards").header("Authorization", token("manager@example.tn")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void varietiesAreSeededAndCachedInRedis() throws Exception {
        redis.delete(OrchardService.VARIETY_CACHE_KEY);

        mockMvc.perform(get("/api/varieties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'CHEMLALI')]").exists());

        assertThat(redis.hasKey(OrchardService.VARIETY_CACHE_KEY)).isTrue();
        varietyRepository.deleteAll();

        mockMvc.perform(get("/api/varieties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("CHEMLALI"));
    }

    @Test
    void orchardIndexesIncludeGeoAndOwner() {
        List<String> indexes = mongoTemplate.indexOps(Orchard.class).getIndexInfo().stream()
                .map(IndexInfo::getName).toList();
        assertThat(indexes).contains("location_2dsphere", "ownerId");
    }

    private String createOrchard(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orchards").header("Authorization", farmerToken(email))
                        .contentType(MediaType.APPLICATION_JSON).content(orchardJson()))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void createFarmer(String email, String memberNumber) {
        userRepository.save(user(email, EnumSet.of(Role.FARMER), memberNumber));
    }

    private User user(String email, EnumSet<Role> roles, String memberNumber) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("secret123"));
        user.setFirstName(email.startsWith("other") ? "Other" : email.startsWith("manager") ? "Collection" : "Ali");
        user.setLastName(email.startsWith("other") ? "Farmer" : email.startsWith("manager") ? "Manager" : "Ben Ali");
        user.setRoles(roles);
        if (memberNumber != null) {
            FarmerProfile profile = new FarmerProfile();
            profile.setMemberNumber(memberNumber);
            profile.setCin("12345678");
            profile.setDelegation(Delegation.SFAX_VILLE);
            user.setFarmerProfile(profile);
        }
        return user;
    }

    private String farmerToken(String email) throws Exception {
        return token(email);
    }

    private String token(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk()).andReturn();
        return "Bearer " + JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String orchardJson() {
        return """
                {"name":"Henchir El Mezraa","delegation":"SAKIET_EZZIT",
                 "location":{"type":"Point","coordinates":[10.7603,34.7998]},
                 "plots":[
                   {"code":"P1","varietyCode":"CHEMLALI","areaHa":4.5,"treeCount":450,"plantingYear":1998,"irrigated":false},
                   {"code":"P2","varietyCode":"CHETOUI","areaHa":1.2,"treeCount":180,"plantingYear":2015,"irrigated":true}
                 ]}
                """;
    }
}
