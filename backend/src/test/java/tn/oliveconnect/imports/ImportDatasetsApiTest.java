package tn.oliveconnect.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MvcResult;
import tn.oliveconnect.AbstractIntegrationTest;
import tn.oliveconnect.mill.Mill;
import tn.oliveconnect.mill.MillRepository;
import tn.oliveconnect.orchard.HarvestStatus;
import tn.oliveconnect.orchard.OliveVariety;
import tn.oliveconnect.orchard.OliveVarietyRepository;
import tn.oliveconnect.orchard.Orchard;
import tn.oliveconnect.orchard.OrchardRepository;
import tn.oliveconnect.resource.Bin;
import tn.oliveconnect.resource.Equipment;
import tn.oliveconnect.resource.EquipmentRepository;
import tn.oliveconnect.resource.Worker;
import tn.oliveconnect.resource.WorkerRepository;
import tn.oliveconnect.user.AdminSeeder;
import tn.oliveconnect.user.FarmerProfile;
import tn.oliveconnect.user.Role;
import tn.oliveconnect.user.User;
import tn.oliveconnect.user.UserRepository;

/** Reference importers (varieties, mills, orchards, resources) and dynamic ones (maturity, mill capacity). */
class ImportDatasetsApiTest extends AbstractIntegrationTest {

    private static final String ORCHARD_HEADER =
            "externalRef;name;memberNumber;lng;lat;delegation;plotCode;varietyCode;areaHa;treeCount;plantingYear;irrigated\n";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    MongoTemplate mongoTemplate;
    @Autowired
    ImportJobRepository jobRepository;
    @Autowired
    OliveVarietyRepository varietyRepository;
    @Autowired
    MillRepository millRepository;
    @Autowired
    OrchardRepository orchardRepository;
    @Autowired
    WorkerRepository workerRepository;
    @Autowired
    EquipmentRepository equipmentRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    AdminSeeder adminSeeder;

    @BeforeEach
    @AfterEach
    void clean() throws IOException {
        jobRepository.deleteAll();
        mongoTemplate.remove(new Query(Criteria.where("externalRef").exists(true)), Orchard.class);
        mongoTemplate.remove(new Query(Criteria.where("externalRef").exists(true)), Worker.class);
        mongoTemplate.remove(new Query(Criteria.where("code").regex("^IMP-")), Equipment.class);
        mongoTemplate.remove(new Query(Criteria.where("code").regex("^IMP-")), Mill.class);
        mongoTemplate.remove(new Query(Criteria.where("_id").regex("^IMP")), OliveVariety.class);
        mongoTemplate.remove(new Query(), MaturityReading.class);
        mongoTemplate.remove(new Query(Criteria.where("farmerProfile.memberNumber").regex("^IMP-")), User.class);
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

    // ---------- varieties.json ----------

    @Test
    void varietiesAreInsertedThenUpdated() throws Exception {
        drop("varieties.json", varietiesJson(19.5, 3.0, 4.5));
        run("REFERENCE").andExpect(jsonPath("$.jobs[0].dataset").value("VARIETIES"))
                .andExpect(jsonPath("$.jobs[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.jobs[0].insertedRows").value(2));
        assertThat(varietyRepository.findById("IMPA").orElseThrow().getAvgOilYieldPct()).isEqualTo(19.5);

        drop("varieties.json", varietiesJson(21, 3.2, 4.8));
        run("REFERENCE").andExpect(jsonPath("$.jobs[0].insertedRows").value(0))
                .andExpect(jsonPath("$.jobs[0].updatedRows").value(2));
        OliveVariety variety = varietyRepository.findById("IMPA").orElseThrow();
        assertThat(variety.getAvgOilYieldPct()).isEqualTo(21);
        assertThat(variety.getOptimalMaturityMax()).isEqualTo(4.8);
    }

    @Test
    void invalidVarietiesAreRejected() throws Exception {
        drop("varieties.json", """
                { "varieties": [
                  { "code": "IMPA", "name": "A", "avgOilYieldPct": 20, "optimalMaturityMin": 3, "optimalMaturityMax": 4 },
                  { "code": "IMPB", "name": "B", "avgOilYieldPct": 20, "optimalMaturityMin": 5, "optimalMaturityMax": 4 },
                  { "code": "IMPC", "name": "C", "avgOilYieldPct": "abc", "optimalMaturityMin": 3, "optimalMaturityMax": 4 },
                  { "code": "IMPD", "name": "D", "avgOilYieldPct": 20, "optimalMaturityMin": 3, "optimalMaturityMax": 4 },
                  { "code": "IMPE", "name": "E", "avgOilYieldPct": 20, "optimalMaturityMin": 3, "optimalMaturityMax": 4 } ] }""");
        run("REFERENCE").andExpect(jsonPath("$.jobs[0].status").value("PARTIAL"))
                .andExpect(jsonPath("$.jobs[0].rejectedRows").value(2))
                .andExpect(jsonPath("$.jobs[0].errors[0].line").value(2))
                .andExpect(jsonPath("$.jobs[0].errors[1].reason").value("'abc' is not a number"));
        assertThat(varietyRepository.existsById("IMPA")).isTrue();
        assertThat(varietyRepository.existsById("IMPB")).isFalse();
    }

    // ---------- mills.json ----------

    @Test
    void millsAreUpsertedAndKeepTheirAvailability() throws Exception {
        drop("mills.json", millsJson(60000, 10.76, 34.82));
        run("REFERENCE").andExpect(jsonPath("$.jobs[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.jobs[0].insertedRows").value(1));
        Mill mill = millRepository.findByCode("IMP-M1").orElseThrow();
        assertThat(mill.getAvailableCapacityKg()).isEqualTo(60000);
        mill.setAvailableCapacityKg(15000);
        millRepository.save(mill);

        drop("mills.json", millsJson(70000, 10.77, 34.83));
        run("REFERENCE").andExpect(jsonPath("$.jobs[0].updatedRows").value(1));
        mill = millRepository.findByCode("IMP-M1").orElseThrow();
        assertThat(mill.getDailyCapacityKg()).isEqualTo(70000);
        assertThat(mill.getAvailableCapacityKg()).isEqualTo(15000);
        assertThat(mill.getLocation().longitude()).isEqualTo(10.77);
    }

    @Test
    void millOutsideTunisiaIsRejected() throws Exception {
        drop("mills.json", millsJson(60000, 2.35, 48.85));
        run("REFERENCE").andExpect(jsonPath("$.jobs[0].status").value("FAILED"))
                .andExpect(jsonPath("$.jobs[0].errors[0].field").value("lng"));
        assertThat(millRepository.findByCode("IMP-M1")).isEmpty();
    }

    // ---------- orchards.csv ----------

    @Test
    void orchardLinesAreGroupedIntoOneOrchardWithPlots() throws Exception {
        saveFarmer("IMP-0421", "Ali", "Ben Ali");
        drop("orchards.csv", ORCHARD_HEADER
                + "IMP-ORC-1;Henchir El Mezraa;IMP-0421;10.7603;34.7998;Sakiet Ezzit;P1;CHEMLALI;4.5;450;1998;false\n"
                + "IMP-ORC-1;Henchir El Mezraa;IMP-0421;10.7603;34.7998;Sakiet Ezzit;P2;CHETOUI;1.2;180;2015;true\n"
                + "IMP-ORC-2;Ain Chaker;IMP-9999;10.60;34.70;Agareb;P1;CHEMLALI;2.0;200;2005;false\n");

        run("REFERENCE").andExpect(jsonPath("$.jobs[0].dataset").value("ORCHARDS"))
                .andExpect(jsonPath("$.jobs[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.jobs[0].totalRows").value(2))
                .andExpect(jsonPath("$.jobs[0].insertedRows").value(2));

        Orchard first = orchardRepository.findAllByExternalRefIn(List.of("IMP-ORC-1")).get(0);
        assertThat(first.getPlots()).hasSize(2);
        assertThat(first.getAreaHa()).isEqualTo(5.7);
        assertThat(first.getTreeCount()).isEqualTo(630);
        assertThat(first.getMainVarietyCode()).isEqualTo("CHEMLALI");
        assertThat(first.getDelegation()).isEqualTo("SAKIET_EZZIT");
        assertThat(first.getOwner()).containsEntry("displayName", "Ali Ben Ali");
        assertThat(first.getOwnerId()).isNotNull();
        Orchard second = orchardRepository.findAllByExternalRefIn(List.of("IMP-ORC-2")).get(0);
        assertThat(second.getOwnerId()).isNull();   // unknown member: created unassigned
    }

    @Test
    void reimportKeepsManualFieldsAndRejectsBadPlots() throws Exception {
        drop("orchards.csv", ORCHARD_HEADER
                + "IMP-ORC-1;Henchir;;10.76;34.80;Sakiet Ezzit;P1;CHEMLALI;4.0;400;1998;false\n"
                + "IMP-ORC-2;Bon;;10.60;34.70;Agareb;P1;CHEMLALI;2.0;200;2005;false\n");
        run("REFERENCE");
        Orchard stored = orchardRepository.findAllByExternalRefIn(List.of("IMP-ORC-1")).get(0);
        stored.setHarvestStatus(HarvestStatus.SCHEDULED);
        stored.setLastSeasonYieldKgHa(2100);
        orchardRepository.save(stored);

        drop("orchards.csv", ORCHARD_HEADER
                + "IMP-ORC-1;Henchir renamed;;10.76;34.80;Sakiet Ezzit;P1;CHEMLALI;4.1;410;1998;false\n"
                + "IMP-ORC-2;Bon;;10.60;34.70;Agareb;P1;XYZ;2.0;200;2005;false\n"
                + "IMP-ORC-3;Trop grand;;10.60;34.70;Agareb;P1;CHEMLALI;-1;200;2005;false\n"
                + "IMP-ORC-4;Nouveau;;10.60;34.70;Agareb;P1;CHEMLALI;1.0;100;2005;false\n"
                + "IMP-ORC-5;Nouveau 2;;10.60;34.70;Agareb;P1;CHEMLALI;1.0;100;2005;false\n");
        run("REFERENCE").andExpect(jsonPath("$.jobs[0].status").value("PARTIAL"))
                .andExpect(jsonPath("$.jobs[0].updatedRows").value(1))
                .andExpect(jsonPath("$.jobs[0].insertedRows").value(2))
                .andExpect(jsonPath("$.jobs[0].errors.length()").value(2))
                .andExpect(jsonPath("$.jobs[0].errors[0].field").value("varietyCode"))
                .andExpect(jsonPath("$.jobs[0].errors[1].field").value("areaHa"))
                .andExpect(jsonPath("$.jobs[0].errors[1].line").value(4));

        Orchard updated = orchardRepository.findAllByExternalRefIn(List.of("IMP-ORC-1")).get(0);
        assertThat(updated.getName()).isEqualTo("Henchir renamed");
        assertThat(updated.getAreaHa()).isEqualTo(4.1);
        assertThat(updated.getHarvestStatus()).isEqualTo(HarvestStatus.SCHEDULED);
        assertThat(updated.getLastSeasonYieldKgHa()).isEqualTo(2100);
        assertThat(orchardRepository.findAllByExternalRefIn(List.of("IMP-ORC-3"))).isEmpty();
    }

    @Test
    void reimportKeepsPlotsThatAreNotInTheFile() throws Exception {
        drop("orchards.csv", ORCHARD_HEADER
                + "IMP-ORC-1;Henchir;;10.76;34.80;Sakiet Ezzit;P1;CHEMLALI;2.0;200;1998;false\n");
        run("REFERENCE");
        Orchard stored = orchardRepository.findAllByExternalRefIn(List.of("IMP-ORC-1")).get(0);
        tn.oliveconnect.orchard.Plot manual = new tn.oliveconnect.orchard.Plot();
        manual.setCode("MANUAL");
        manual.setVarietyCode("CHEMLALI");
        manual.setAreaHa(0.5);
        stored.getPlots().add(manual);
        orchardRepository.save(stored);

        drop("orchards.csv", ORCHARD_HEADER
                + "IMP-ORC-1;Henchir;;10.76;34.80;Sakiet Ezzit;P1;CHEMLALI;2.1;210;1998;false\n");
        run("REFERENCE");

        Orchard updated = orchardRepository.findAllByExternalRefIn(List.of("IMP-ORC-1")).get(0);
        assertThat(updated.getPlots()).extracting(tn.oliveconnect.orchard.Plot::getCode).containsExactly("P1", "MANUAL");
        assertThat(updated.getPlots().get(0).getAreaHa()).isEqualTo(2.1);
    }

    @Test
    void areaGrowingMoreThanFivePercentIsRejected() throws Exception {
        drop("orchards.csv", ORCHARD_HEADER + "IMP-ORC-1;Henchir;;10.76;34.80;Agareb;P1;CHEMLALI;4.0;400;1998;false\n"
                + "IMP-ORC-2;Autre;;10.60;34.70;Agareb;P1;CHEMLALI;2.0;200;2005;false\n");
        run("REFERENCE");
        drop("orchards.csv", ORCHARD_HEADER + "IMP-ORC-1;Henchir;;10.76;34.80;Agareb;P1;CHEMLALI;4.5;400;1998;false\n"
                + "IMP-ORC-2;Autre;;10.60;34.70;Agareb;P1;CHEMLALI;2.0;200;2005;false\n");
        run("REFERENCE").andExpect(jsonPath("$.jobs[0].status").value("PARTIAL"))
                .andExpect(jsonPath("$.jobs[0].errors[0].field").value("areaHa"));
        assertThat(orchardRepository.findAllByExternalRefIn(List.of("IMP-ORC-1")).get(0).getAreaHa()).isEqualTo(4.0);
    }

    @Test
    void orchardFileWithoutLatColumnFails() throws Exception {
        drop("orchards.csv", "externalRef;name;memberNumber;lng;delegation;plotCode;varietyCode;areaHa;treeCount;plantingYear;irrigated\n");
        run("REFERENCE").andExpect(jsonPath("$.jobs[0].status").value("FAILED"))
                .andExpect(jsonPath("$.jobs[0].errors[0].reason").value("FILE_FORMAT: missing column 'lat'"));
        assertThat(INBOX.resolve("rejected/orchards.csv")).exists();
    }

    // ---------- resources.csv ----------

    @Test
    void workersAndEquipmentAreImportedThenUpdatedWithoutLosingManualFields() throws Exception {
        drop("resources.csv", "externalRef;type;name;phone;capacityKg\n"
                + "IMP-W1;WORKER;Ahmed Ben Salah;20123456;\n"
                + "IMP-T1;TRACTOR;Tractor 1;;\n"
                + "IMP-B1;BIN;Bin 1;;400\n");
        run("REFERENCE").andExpect(jsonPath("$.jobs[0].dataset").value("RESOURCES"))
                .andExpect(jsonPath("$.jobs[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.jobs[0].insertedRows").value(3));
        Worker worker = workerRepository.findAllByExternalRefIn(List.of("IMP-W1")).get(0);
        assertThat(worker.getFirstName()).isEqualTo("Ahmed");
        assertThat(worker.getLastName()).isEqualTo("Ben Salah");
        worker.setDailyRate(45);
        workerRepository.save(worker);

        drop("resources.csv", "externalRef;type;name;phone;capacityKg\n"
                + "IMP-W1;WORKER;Ahmed Ben Salah;29999999;\n"
                + "IMP-B1;BIN;Bin 1;;500\n");
        run("REFERENCE").andExpect(jsonPath("$.jobs[0].updatedRows").value(2));
        worker = workerRepository.findAllByExternalRefIn(List.of("IMP-W1")).get(0);
        assertThat(worker.getPhone()).isEqualTo("29999999");
        assertThat(worker.getDailyRate()).isEqualTo(45);
        assertThat(((Bin) equipmentRepository.findByCode("IMP-B1").orElseThrow()).getCapacityKg()).isEqualTo(500);
        assertThat(equipmentRepository.findByCode("IMP-T1").orElseThrow().getType().name()).isEqualTo("TRACTOR");
    }

    @Test
    void unknownResourceTypeAndDuplicateAreRejected() throws Exception {
        drop("resources.csv", "externalRef;type;name;phone;capacityKg\n"
                + "IMP-W1;WORKER;Ahmed Ben Salah;;\n"
                + "IMP-X1;FORKLIFT;Forklift;;\n"
                + "IMP-W1;WORKER;Ahmed again;;\n"
                + "IMP-B1;BIN;Bin 1;;abc\n"
                + "IMP-B2;BIN;Bin 2;;300\n"
                + "IMP-B3;BIN;Bin 3;;300\n"
                + "IMP-B4;BIN;Bin 4;;300\n"
                + "IMP-B5;BIN;Bin 5;;300\n");
        run("REFERENCE").andExpect(jsonPath("$.jobs[0].status").value("PARTIAL"))
                .andExpect(jsonPath("$.jobs[0].rejectedRows").value(3))
                .andExpect(jsonPath("$.jobs[0].errors[0].line").value(3))
                .andExpect(jsonPath("$.jobs[0].errors[0].field").value("type"))
                .andExpect(jsonPath("$.jobs[0].errors[1].reason").value("duplicate externalRef 'IMP-W1' in the file"))
                .andExpect(jsonPath("$.jobs[0].errors[2].reason").value("'abc' is not a number"));
        assertThat(workerRepository.findAllByExternalRefIn(List.of("IMP-W1"))).hasSize(1);
    }

    // ---------- maturity_*.csv ----------

    @Test
    void maturityReadingsAreStoredAndOrchardFollowsTheLatest() throws Exception {
        importOrchard("IMP-ORC-1");
        drop("maturity_202511210600.csv", "orchardExternalRef;orchardTimestamp;x\n");   // not matching the header: failed
        run("DYNAMIC").andExpect(jsonPath("$.jobs[0].status").value("FAILED"));

        drop("maturity_202511210615.csv", "orchardExternalRef;timestamp;maturityIndex;source\n"
                + "IMP-ORC-1;2025-11-21T06:00:00Z;3.8;SENSOR\n"
                + "IMP-ORC-1;2025-11-21T09:00:00Z;3.9;SENSOR\n"
                + "IMP-ORC-1;2025-11-21T03:00:00Z;3.7;SENSOR\n");
        run("DYNAMIC").andExpect(jsonPath("$.jobs[0].dataset").value("MATURITY"))
                .andExpect(jsonPath("$.jobs[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.jobs[0].insertedRows").value(3));

        assertThat(mongoTemplate.count(new Query(), MaturityReading.class)).isEqualTo(3);
        Orchard orchard = orchardRepository.findAllByExternalRefIn(List.of("IMP-ORC-1")).get(0);
        assertThat(orchard.getCurrentMaturityIndex()).isEqualTo(3.9);
        assertThat(orchard.getMaturityUpdatedAt()).hasToString("2025-11-21T09:00:00Z");
        MaturityReading stored = mongoTemplate.findOne(new Query(), MaturityReading.class);
        assertThat(stored.meta().orchardId()).isEqualTo(orchard.getId());
        assertThat(stored.varietyCode()).isEqualTo("CHEMLALI");

        // an older file does not move the orchard back
        drop("maturity_202511210630.csv", "orchardExternalRef;timestamp;maturityIndex;source\n"
                + "IMP-ORC-1;2025-11-20T06:00:00Z;3.1;SENSOR\n");
        run("DYNAMIC");
        assertThat(orchardRepository.findAllByExternalRefIn(List.of("IMP-ORC-1")).get(0).getCurrentMaturityIndex())
                .isEqualTo(3.9);
    }

    @Test
    void invalidMaturityLinesAreRejected() throws Exception {
        importOrchard("IMP-ORC-1");
        drop("maturity_202511210645.csv", "orchardExternalRef;timestamp;maturityIndex;source\n"
                + "IMP-ORC-1;2025-11-21T06:00:00Z;3.8;SENSOR\n"
                + "IMP-ORC-1;2025-11-21T06:00:00Z;3.8;SENSOR\n"
                + "IMP-ORC-1;2025-11-21T07:00:00Z;9.5;SENSOR\n"
                + "IMP-ORC-9;2025-11-21T08:00:00Z;3.0;SENSOR\n"
                + "IMP-ORC-1;2025-11-21T09:00:00Z;abc;SENSOR\n"
                + "IMP-ORC-1;2025-11-21T10:00:00Z;4.0;SENSOR\n"
                + "IMP-ORC-1;2025-11-21T11:00:00Z;4.1;SENSOR\n"
                + "IMP-ORC-1;2025-11-21T12:00:00Z;4.2;SENSOR\n"
                + "IMP-ORC-1;2025-11-21T13:00:00Z;4.3;SENSOR\n");
        run("DYNAMIC").andExpect(jsonPath("$.jobs[0].status").value("PARTIAL"))
                .andExpect(jsonPath("$.jobs[0].rejectedRows").value(4))
                .andExpect(jsonPath("$.jobs[0].errors[0].line").value(3))
                .andExpect(jsonPath("$.jobs[0].errors[0].reason").value("duplicate reading"))
                .andExpect(jsonPath("$.jobs[0].errors[1].field").value("maturityIndex"))
                .andExpect(jsonPath("$.jobs[0].errors[2].reason").value("orchardExternalRef 'IMP-ORC-9' unknown"))
                .andExpect(jsonPath("$.jobs[0].errors[3].reason").value("'abc' is not a number"));
        assertThat(mongoTemplate.count(new Query(), MaturityReading.class)).isEqualTo(5);
    }

    @Test
    void maturityReadingsIsATimeSeriesCollection() {
        var info = mongoTemplate.executeCommand("{ listCollections: 1, filter: { name: 'maturity_readings' } }");
        assertThat(info.toJson()).contains("timeseries").contains("\"timeField\": \"timestamp\"")
                .contains("\"metaField\": \"meta\"");
    }

    // ---------- mill_capacity_*.json ----------

    @Test
    void millCapacityUpdatesAvailableCapacity() throws Exception {
        drop("mills.json", millsJson(60000, 10.76, 34.82));
        run("REFERENCE");
        drop("mill_capacity_20251121.json", """
                { "date": "2025-11-21", "mills": [
                  { "millCode": "IMP-M1", "dailyCapacityKg": 60000, "availableCapacityKg": 42000 } ] }""");
        run("DYNAMIC").andExpect(jsonPath("$.jobs[0].dataset").value("MILL_CAPACITY"))
                .andExpect(jsonPath("$.jobs[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.jobs[0].updatedRows").value(1));
        assertThat(millRepository.findByCode("IMP-M1").orElseThrow().getAvailableCapacityKg()).isEqualTo(42000);
    }

    @Test
    void olderMillCapacityFileArrivingLateDoesNotOverwriteNewerData() throws Exception {
        drop("mills.json", millsJson(60000, 10.76, 34.82));
        run("REFERENCE");
        drop("mill_capacity_20251122.json", """
                { "date": "2025-11-22", "mills": [
                  { "millCode": "IMP-M1", "dailyCapacityKg": 60000, "availableCapacityKg": 30000 } ] }""");
        run("DYNAMIC");
        drop("mill_capacity_20251121.json", """
                { "date": "2025-11-21", "mills": [
                  { "millCode": "IMP-M1", "dailyCapacityKg": 60000, "availableCapacityKg": 42000 } ] }""");
        run("DYNAMIC");
        assertThat(millRepository.findByCode("IMP-M1").orElseThrow().getAvailableCapacityKg()).isEqualTo(30000);
    }

    @Test
    void millCapacityRejectsUnknownMillAndNegativeValues() throws Exception {
        drop("mills.json", """
                { "mills": [
                  { "code": "IMP-M1", "name": "A", "lng": 10.7, "lat": 34.8, "dailyCapacityKg": 60000 },
                  { "code": "IMP-M2", "name": "B", "lng": 10.7, "lat": 34.8, "dailyCapacityKg": 60000 },
                  { "code": "IMP-M3", "name": "C", "lng": 10.7, "lat": 34.8, "dailyCapacityKg": 60000 } ] }""");
        run("REFERENCE");
        drop("mill_capacity_20251122.json", """
                { "date": "2025-11-22", "mills": [
                  { "millCode": "IMP-M1", "dailyCapacityKg": 60000, "availableCapacityKg": 30000 },
                  { "millCode": "MIL-XXX-99", "dailyCapacityKg": 60000, "availableCapacityKg": 30000 },
                  { "millCode": "IMP-M2", "dailyCapacityKg": 60000, "availableCapacityKg": -500 },
                  { "millCode": "IMP-M3", "dailyCapacityKg": 60000, "availableCapacityKg": 1000 } ] }""");
        run("DYNAMIC").andExpect(jsonPath("$.jobs[0].status").value("PARTIAL"))
                .andExpect(jsonPath("$.jobs[0].errors[0].line").value(2))
                .andExpect(jsonPath("$.jobs[0].errors[0].reason").value("'MIL-XXX-99' unknown"))
                .andExpect(jsonPath("$.jobs[0].errors[1].line").value(3))
                .andExpect(jsonPath("$.jobs[0].errors[1].field").value("availableCapacityKg"));
        assertThat(millRepository.findByCode("IMP-M1").orElseThrow().getAvailableCapacityKg()).isEqualTo(30000);
    }

    // ---------- helpers ----------

    private String varietiesJson(double yield, double min, double max) {
        return """
                { "generatedAt": "2025-11-21T02:00:00Z", "varieties": [
                  { "code": "IMPA", "name": "A", "avgOilYieldPct": %s, "optimalMaturityMin": %s, "optimalMaturityMax": %s },
                  { "code": "IMPB", "name": "B", "avgOilYieldPct": 18, "optimalMaturityMin": 3.5, "optimalMaturityMax": 5 } ] }"""
                .formatted(yield, min, max);
    }

    private String millsJson(int capacity, double lng, double lat) {
        return """
                { "generatedAt": "2025-11-21T02:00:00Z", "mills": [
                  { "code": "IMP-M1", "name": "Huilerie", "lng": %s, "lat": %s, "dailyCapacityKg": %d } ] }"""
                .formatted(lng, lat, capacity);
    }

    private void importOrchard(String ref) throws Exception {
        drop("orchards.csv", ORCHARD_HEADER + ref + ";Henchir;;10.76;34.80;Sakiet Ezzit;P1;CHEMLALI;4.0;400;1998;false\n");
        run("REFERENCE").andExpect(jsonPath("$.jobs[0].status").value("SUCCESS"));
    }

    private void saveFarmer(String memberNumber, String firstName, String lastName) {
        User farmer = new User();
        farmer.setEmail(memberNumber.toLowerCase() + "@example.tn");
        farmer.setPasswordHash("x");
        farmer.setFirstName(firstName);
        farmer.setLastName(lastName);
        farmer.setRoles(EnumSet.of(Role.FARMER));
        FarmerProfile profile = new FarmerProfile();
        profile.setMemberNumber(memberNumber);
        farmer.setFarmerProfile(profile);
        userRepository.save(farmer);
    }

    private ResultActions run(String type) throws Exception {
        return mockMvc.perform(post("/api/imports/run").param("type", type).header("Authorization", adminToken()))
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
