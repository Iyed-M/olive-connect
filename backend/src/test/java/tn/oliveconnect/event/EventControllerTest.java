package tn.oliveconnect.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.EnumSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tn.oliveconnect.AbstractIntegrationTest;
import tn.oliveconnect.notification.NotificationRepository;
import tn.oliveconnect.notification.NotificationService;
import tn.oliveconnect.notification.NotificationType;
import tn.oliveconnect.user.Role;
import tn.oliveconnect.user.User;
import tn.oliveconnect.user.UserRepository;

class EventControllerTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    NotificationRepository notificationRepository;
    @Autowired
    NotificationService notificationService;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    SseEmitterRegistry emitterRegistry;

    @BeforeEach
    void clean() {
        notificationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterEach
    void closeEmitters() {
        emitterRegistry.closeAll();
    }

    @Test
    void authenticatedUserGetsOneTimeTicketAndReuseIsRejected() throws Exception {
        User user = saveUser("events-ticket@example.tn", Role.FARMER);
        String authorization = token(user);

        MvcResult ticketResult = mockMvc.perform(post("/api/events/ticket")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(30))
                .andReturn();
        String ticket = JsonPath.read(ticketResult.getResponse().getContentAsString(), "$.ticket");

        mockMvc.perform(get("/api/events").param("ticket", ticket).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted());

        mockMvc.perform(get("/api/events").param("ticket", ticket))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void invalidTicketIsRejectedWithoutAnAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/events").param("ticket", "not-a-ticket"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void persistedNotificationIsFannedOutOnlyToItsRecipient() throws Exception {
        User recipient = saveUser("recipient@example.tn", Role.FARMER);
        User otherUser = saveUser("other-recipient@example.tn", Role.FARMER);
        SseEmitter recipientEmitter = mock(SseEmitter.class);
        SseEmitter otherEmitter = mock(SseEmitter.class);
        emitterRegistry.register(recipient.getId(), recipientEmitter);
        emitterRegistry.register(otherUser.getId(), otherEmitter);

        notificationService.notifyUser(recipient.getId(), NotificationType.REQUEST_VALIDATED,
                "Request validated", "Your request was validated", "/requests/1");

        verify(recipientEmitter, timeout(2_000)).send(any(SseEmitter.SseEventBuilder.class));
        verify(otherEmitter, timeout(250).times(0)).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipient.getId()))
                .hasSize(1);
        assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(otherUser.getId()))
                .isEmpty();
    }

    @Test
    void connectedClientReceivesNotificationAndAlertEvents() throws Exception {
        User recipient = saveUser("stream-recipient@example.tn", Role.FARMER);
        String ticket = eventTicket(token(recipient));
        MvcResult stream = mockMvc.perform(get("/api/events").param("ticket", ticket)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        notificationService.notifyUser(recipient.getId(), NotificationType.REQUEST_VALIDATED,
                "Request validated", "Your request was validated", "/requests/1");
        waitForResponse(stream, "event:notification");
        notificationService.notifyUser(recipient.getId(), NotificationType.NEW_ALERT,
                "New alert", "A field alert was reported", "/alerts/1");
        waitForResponse(stream, "event:alert");

        assertThat(stream.getResponse().getContentAsString())
                .contains("REQUEST_VALIDATED")
                .contains("NEW_ALERT");
    }

    private String eventTicket(String authorization) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/events/ticket")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.ticket");
    }

    private void waitForResponse(MvcResult result, String text) throws Exception {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (!result.getResponse().getContentAsString().contains(text)
                && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(result.getResponse().getContentAsString()).contains(text);
    }

    private User saveUser(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("secret123"));
        user.setFirstName("Test");
        user.setLastName("User");
        user.setRoles(EnumSet.of(role));
        return userRepository.save(user);
    }

    private String token(User user) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail()
                                + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }
}
