package tn.oliveconnect.resource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
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

class ResourceCalendarTest extends AbstractIntegrationTest {

    static final String WEEK = "from=2026-11-23T00:00:00Z&to=2026-11-30T00:00:00Z";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired EquipmentRepository equipmentRepository;
    @Autowired ResourceBookingRepository bookingRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void clean() {
        equipmentRepository.deleteAll();
        bookingRepository.deleteAll();
    }

    private String tokenFor(Role role) throws Exception {
        String email = role.name().toLowerCase() + "@calendar-test.tn";
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

    private Bin saveBin() {
        Bin bin = new Bin();
        bin.setCode("BIN-1");
        bin.setCapacityKg(4000);
        bin.getUnavailabilities().add(new Unavailability(Instant.parse("2026-11-25T00:00:00Z"),
                Instant.parse("2026-11-26T00:00:00Z"), "Repair"));
        bin.getUnavailabilities().add(new Unavailability(Instant.parse("2026-12-10T00:00:00Z"),
                Instant.parse("2026-12-11T00:00:00Z"), "Outside the week"));
        return equipmentRepository.save(bin);
    }

    @Test
    @DisplayName("T-RS-1 calendar of a bin for a week: bookings and unavailabilities")
    void binCalendar() throws Exception {
        Bin bin = saveBin();
        bookingRepository.save(ResourceBooking.forEquipment(bin.getId(), EquipmentType.BIN, "round-1",
                Instant.parse("2026-11-24T07:00:00Z"), Instant.parse("2026-11-24T15:00:00Z")));
        ResourceBooking released = ResourceBooking.forEquipment(bin.getId(), EquipmentType.BIN, "round-2",
                Instant.parse("2026-11-27T07:00:00Z"), Instant.parse("2026-11-27T15:00:00Z"));
        released.setStatus(BookingStatus.RELEASED);
        bookingRepository.save(released);
        bookingRepository.save(ResourceBooking.forEquipment(bin.getId(), EquipmentType.BIN, "round-3",
                Instant.parse("2026-12-02T07:00:00Z"), Instant.parse("2026-12-02T15:00:00Z")));

        mockMvc.perform(get("/api/resources/calendar?resourceId=" + bin.getId() + "&" + WEEK)
                        .header("Authorization", tokenFor(Role.LOGISTICS_MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceKind").value("EQUIPMENT"))
                .andExpect(jsonPath("$.label").value("BIN-1"))
                .andExpect(jsonPath("$.bookings.length()").value(1))
                .andExpect(jsonPath("$.bookings[0].roundId").value("round-1"))
                .andExpect(jsonPath("$.unavailabilities.length()").value(1))
                .andExpect(jsonPath("$.unavailabilities[0].reason").value("Repair"));
    }

    @Test
    @DisplayName("T-RS-1 errors: unknown resource, bad range, missing parameter, forbidden role")
    void errors() throws Exception {
        Bin bin = saveBin();
        String token = tokenFor(Role.COLLECTION_MANAGER);

        mockMvc.perform(get("/api/resources/calendar?resourceId=nope&" + WEEK).header("Authorization", token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/resources/calendar?resourceId=" + bin.getId()
                        + "&from=2026-11-30T00:00:00Z&to=2026-11-23T00:00:00Z").header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/resources/calendar?" + WEEK).header("Authorization", token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/resources/calendar?resourceId=" + bin.getId() + "&" + WEEK)
                        .header("Authorization", tokenFor(Role.FARMER)))
                .andExpect(status().isForbidden());
    }
}
