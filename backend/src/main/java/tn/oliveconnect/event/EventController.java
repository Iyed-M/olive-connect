package tn.oliveconnect.event;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tn.oliveconnect.auth.UnauthenticatedException;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventTicketService ticketService;
    private final SseEmitterRegistry emitterRegistry;

    public EventController(EventTicketService ticketService, SseEmitterRegistry emitterRegistry) {
        this.ticketService = ticketService;
        this.emitterRegistry = emitterRegistry;
    }

    @PostMapping("/ticket")
    public EventTicketDto ticket(Authentication authentication) {
        return ticketService.issue(authentication.getName());
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(required = false) String ticket) {
        String userId = ticketService.consume(ticket)
                .orElseThrow(() -> new UnauthenticatedException("The event ticket is invalid or expired"));
        return emitterRegistry.register(userId);
    }
}
