package tn.oliveconnect.event;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class EventTicketService {

    static final Duration LIFETIME = Duration.ofSeconds(30);
    private static final String KEY_PREFIX = "event:ticket:";

    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    public EventTicketService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public EventTicketDto issue(String userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(key(ticket), userId, LIFETIME);
        return new EventTicketDto(ticket, LIFETIME.toSeconds());
    }

    /** Atomic GETDEL makes a ticket usable by only one connection, even when requests race. */
    public Optional<String> consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(redis.opsForValue().getAndDelete(key(ticket)));
    }

    private String key(String ticket) {
        return KEY_PREFIX + ticket;
    }
}
