package tn.oliveconnect.auth;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Brute-force protection (docs/05 section 7): counts failed logins per email and IP in Redis.
 * The 15 min window starts at the first failure and is not extended by later failures.
 */
@Component
public class LoginRateLimiter {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final StringRedisTemplate redis;

    public LoginRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Throws TooManyAttemptsException when this email and IP already have 5 failures in the window. */
    public void checkAllowed(String email, String ip) {
        String value = redis.opsForValue().get(key(email, ip));
        if (value != null && Long.parseLong(value) >= MAX_FAILURES) {
            Long ttl = redis.getExpire(key(email, ip), TimeUnit.SECONDS);
            throw new TooManyAttemptsException(ttl == null || ttl < 1 ? 1 : ttl);
        }
    }

    public void recordFailure(String email, String ip) {
        Long failures = redis.opsForValue().increment(key(email, ip));
        if (failures != null && failures == 1) {
            redis.expire(key(email, ip), WINDOW);   // only the first failure starts the window
        }
    }

    /** A successful login forgets the previous failures. */
    public void reset(String email, String ip) {
        redis.delete(key(email, ip));
    }

    private String key(String email, String ip) {
        return "ratelimit:login:" + email + ":" + ip;
    }
}
