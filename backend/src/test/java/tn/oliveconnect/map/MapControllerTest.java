package tn.oliveconnect.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.QueryMapper;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tn.oliveconnect.AbstractIntegrationTest;
import tn.oliveconnect.orchard.GeoPoint;
import tn.oliveconnect.orchard.HarvestStatus;
import tn.oliveconnect.orchard.Orchard;
import tn.oliveconnect.orchard.Plot;

class MapControllerTest extends AbstractIntegrationTest {

    static final String SFAX = "10.5,34.5,11.1,35.1";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    MongoTemplate mongoTemplate;
    @Autowired
    StringRedisTemplate redis;

    @BeforeEach
    void clean() {
        mongoTemplate.remove(new Query(), Orchard.class);
        var keys = redis.keys("cache:mapPublic::*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    void publicWithoutTokenReturnsOnlyAnonymousFeaturesInsideBbox() throws Exception {
        mongoTemplate.save(orchard("Ali Ben Salah farm", 10.76, 34.74, "CHEMLALI", HarvestStatus.NOT_HARVESTED, 3.2));
        mongoTemplate.save(orchard("Far away", 9.0, 36.0, "CHEMLALI", HarvestStatus.NOT_HARVESTED, 3.2));

        var body = mockMvc.perform(get("/api/map/public").param("bbox", SFAX))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features.length()").value(1))
                .andExpect(jsonPath("$.features[0].geometry.type").value("Point"))
                .andExpect(jsonPath("$.features[0].properties.varietyCode").value("CHEMLALI"))
                .andExpect(jsonPath("$.features[0].properties.harvestStatus").value("NOT_HARVESTED"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("Ali Ben Salah", "owner", "phone", "yield", "Yield", "Secret");
    }

    @Test
    void filtersByVarietyStatusAndMaturity() throws Exception {
        mongoTemplate.save(orchard("a", 10.7, 34.7, "CHEMLALI", HarvestStatus.NOT_HARVESTED, 2.0));
        mongoTemplate.save(orchard("b", 10.71, 34.71, "CHETOUI", HarvestStatus.NOT_HARVESTED, 2.0));
        mongoTemplate.save(orchard("c", 10.72, 34.72, "CHEMLALI", HarvestStatus.HARVESTED, 2.0));
        mongoTemplate.save(orchard("d", 10.73, 34.73, "CHEMLALI", HarvestStatus.NOT_HARVESTED, 5.0));

        mockMvc.perform(get("/api/map/public").param("bbox", SFAX).param("variety", "CHEMLALI")
                        .param("status", "NOT_HARVESTED").param("maturityMin", "1").param("maturityMax", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.length()").value(1));
    }

    @Test
    void lowZoomGroupsNearbyOrchardsIntoClusters() throws Exception {
        for (int i = 0; i < 5; i++) {
            mongoTemplate.save(orchard("o" + i, 10.70 + i * 0.001, 34.70, "CHEMLALI", HarvestStatus.NOT_HARVESTED, 2.0));
        }

        mockMvc.perform(get("/api/map/public").param("bbox", SFAX).param("zoom", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.length()").value(1))
                .andExpect(jsonPath("$.features[0].properties.cluster").value(true))
                .andExpect(jsonPath("$.features[0].properties.count").value(5));
    }

    @Test
    void resultIsCachedInRedisWithShortTtl() throws Exception {
        mongoTemplate.save(orchard("a", 10.7, 34.7, "CHEMLALI", HarvestStatus.NOT_HARVESTED, 2.0));
        mockMvc.perform(get("/api/map/public").param("bbox", SFAX)).andExpect(status().isOk());

        var keys = redis.keys("cache:mapPublic::*");
        assertThat(keys).hasSize(1);
        assertThat(redis.getExpire(keys.iterator().next())).isBetween(1L, 60L);

        // a new orchard is not visible until the cache entry expires
        mongoTemplate.save(orchard("b", 10.8, 34.8, "CHEMLALI", HarvestStatus.NOT_HARVESTED, 2.0));
        mockMvc.perform(get("/api/map/public").param("bbox", SFAX))
                .andExpect(jsonPath("$.features.length()").value(1));
    }

    @Test
    void invalidParametersReturn400() throws Exception {
        mockMvc.perform(get("/api/map/public").param("bbox", "1,2,3"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/map/public").param("bbox", SFAX).param("status", "NOPE"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/map/public").param("bbox", "11,35,10,34"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/map/public").param("bbox", SFAX).param("delegation", "NOWHERE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/map/public").param("bbox", "5,30,12,36"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/map/public").param("bbox", SFAX).param("maturityMin", "NaN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void filtersByDelegationAndSharesTheCacheEntryForEqualVarietyWritings() throws Exception {
        Orchard other = orchard("b", 10.71, 34.71, "CHEMLALI", HarvestStatus.NOT_HARVESTED, 2.0);
        other.setDelegation("THYNA");
        mongoTemplate.save(orchard("a", 10.7, 34.7, "CHEMLALI", HarvestStatus.NOT_HARVESTED, 2.0));
        mongoTemplate.save(other);

        mockMvc.perform(get("/api/map/public").param("bbox", SFAX).param("delegation", "thyna"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.length()").value(1));

        mockMvc.perform(get("/api/map/public").param("bbox", SFAX).param("variety", "chemlali"));
        mockMvc.perform(get("/api/map/public").param("bbox", SFAX).param("variety", " CHEMLALI "));
        assertThat(redis.keys("cache:mapPublic::*")).hasSize(2);
    }

    @Test
    void publicFeatureHasNoPersonalFields() {
        List<String> names = new ArrayList<>();
        collectFieldNames(PublicOrchardFeature.class, names);
        assertThat(names).isNotEmpty()
                .noneMatch(n -> n.toLowerCase().contains("owner"))
                .noneMatch(n -> n.toLowerCase().contains("phone"))
                .noneMatch(n -> n.toLowerCase().contains("yield"))
                .noneMatch(n -> n.toLowerCase().contains("email"))
                .noneMatch(n -> n.toLowerCase().contains("name"));
    }

    private void collectFieldNames(Class<?> type, List<String> names) {
        for (RecordComponent component : type.getRecordComponents()) {
            names.add(component.getName());
            if (component.getType().isRecord()) {
                collectFieldNames(component.getType(), names);
            }
        }
    }

    @Test
    void boxQueryUsesTheTwoDSphereIndex() {
        mongoTemplate.save(orchard("a", 10.7, 34.7, "CHEMLALI", HarvestStatus.NOT_HARVESTED, 2.0));
        // The area filter is the one MapService builds, mapped to a Mongo document like MongoTemplate does.
        Query areaQuery = new Query(MapService.areaCriteria(new double[] {10.5, 34.5, 11.1, 35.1}));
        Document filter = new QueryMapper(mongoTemplate.getConverter())
                .getMappedObject(areaQuery.getQueryObject(), mongoTemplate.getConverter().getMappingContext()
                        .getPersistentEntity(Orchard.class));
        Document plan = mongoTemplate.getDb().runCommand(new Document("explain",
                new Document("find", "orchards").append("filter", filter)).append("verbosity", "queryPlanner"));

        String json = plan.toJson();
        assertThat(json).contains("IXSCAN").contains("location_2dsphere").doesNotContain("COLLSCAN");
    }

    @Test
    void answersUnderTwoSecondsWith1500Orchards() throws Exception {
        List<Orchard> orchards = new ArrayList<>();
        for (int i = 0; i < 1500; i++) {
            double lng = 10.5 + (i % 50) * 0.012;
            double lat = 34.6 + (i / 50) * 0.01;
            orchards.add(orchard("o" + i, lng, lat, i % 2 == 0 ? "CHEMLALI" : "CHETOUI",
                    HarvestStatus.values()[i % 4], i % 10));
        }
        mongoTemplate.insertAll(orchards);

        long start = System.nanoTime();
        mockMvc.perform(get("/api/map/public").param("bbox", SFAX).param("zoom", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.length()").value(1500));
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertThat(millis).isLessThan(2000);
    }

    private Orchard orchard(String name, double lng, double lat, String variety, HarvestStatus harvestStatus,
            double maturity) {
        Orchard orchard = new Orchard();
        orchard.setName(name);
        orchard.setOwnerId("owner-1");
        orchard.setOwner(Map.of("displayName", "Secret Farmer"));
        orchard.setLocation(new GeoPoint(lng, lat));
        orchard.setDelegation("SAKIET_EZZIT");
        Plot plot = new Plot();
        plot.setCode("P1");
        plot.setVarietyCode(variety);
        plot.setAreaHa(1);
        plot.setTreeCount(100);
        orchard.setPlots(new ArrayList<>(Arrays.asList(plot)));
        orchard.recomputeTotals();
        orchard.setHarvestStatus(harvestStatus);
        orchard.setCurrentMaturityIndex(maturity);
        orchard.setLastSeasonYieldKgHa(1234);
        return orchard;
    }
}
