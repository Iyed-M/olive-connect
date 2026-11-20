package tn.oliveconnect.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import tn.oliveconnect.AbstractIntegrationTest;

class LoginRateLimiterTest extends AbstractIntegrationTest {

    static final String EMAIL = "a@b.tn";
    static final String IP = "10.0.0.1";
    static final String KEY = "ratelimit:login:a@b.tn:10.0.0.1";

    @Autowired
    LoginRateLimiter limiter;
    @Autowired
    StringRedisTemplate redis;

    @Test
    void allowsUpToFourFailures() {
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(EMAIL, IP);
        }
        assertDoesNotThrow(() -> limiter.checkAllowed(EMAIL, IP));
    }

    @Test
    void blocksAfterFiveFailuresWithRetryAfterInsideTheWindow() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(EMAIL, IP);
        }
        TooManyAttemptsException ex = assertThrows(TooManyAttemptsException.class,
                () -> limiter.checkAllowed(EMAIL, IP));
        assertTrue(ex.getRetryAfterSeconds() > 0 && ex.getRetryAfterSeconds() <= 900);
    }

    @Test
    void resetClearsTheFailures() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(EMAIL, IP);
        }
        limiter.reset(EMAIL, IP);
        assertDoesNotThrow(() -> limiter.checkAllowed(EMAIL, IP));
    }

    @Test
    void windowIsNotExtendedByLaterFailures() {
        limiter.recordFailure(EMAIL, IP);
        redis.expire(KEY, java.time.Duration.ofSeconds(100));
        limiter.recordFailure(EMAIL, IP);
        assertTrue(redis.getExpire(KEY) <= 100);
    }

    @Test
    void counterLeftWithoutTtlGetsOne() {
        redis.opsForValue().set(KEY, "2");   // no TTL
        limiter.recordFailure(EMAIL, IP);
        assertTrue(redis.getExpire(KEY) > 0);
    }

    @Test
    void differentIpIsCountedSeparately() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(EMAIL, IP);
        }
        assertDoesNotThrow(() -> limiter.checkAllowed(EMAIL, "10.0.0.2"));
    }
}
