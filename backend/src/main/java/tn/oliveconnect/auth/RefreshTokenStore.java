package tn.oliveconnect.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Refresh sessions in Redis (docs/05 section 7):
 * refresh:{tokenId} is a hash {userId, exp}, user:{userId}:refresh is the set of a User's tokenIds.
 */
@Component
public class RefreshTokenStore {

    static final Duration LIFETIME = Duration.ofDays(7);

    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Opens a session for the User and returns its opaque token id. */
    public String create(String userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String tokenId = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        redis.opsForHash().putAll(tokenKey(tokenId),
                Map.of("userId", userId, "exp", String.valueOf(Instant.now().plus(LIFETIME).getEpochSecond())));
        redis.expire(tokenKey(tokenId), LIFETIME);
        redis.opsForSet().add(userKey(userId), tokenId);
        redis.expire(userKey(userId), LIFETIME);
        return tokenId;
    }

    /**
     * Single use: reads the session and deletes it. The delete tells us whether we won the race,
     * so two concurrent requests with the same id cannot both succeed.
     */
    public Optional<String> consume(String tokenId) {
        Object userId = redis.opsForHash().get(tokenKey(tokenId), "userId");
        if (userId == null || !Boolean.TRUE.equals(redis.delete(tokenKey(tokenId)))) {
            return Optional.empty();
        }
        redis.opsForSet().remove(userKey(userId.toString()), tokenId);
        return Optional.of(userId.toString());
    }

    /** Ends one session. Unknown ids are ignored (logout is idempotent). */
    public void revoke(String tokenId) {
        consume(tokenId);
    }

    /** Ends every session of the User. */
    public void revokeAll(String userId) {
        var tokenIds = redis.opsForSet().members(userKey(userId));
        if (tokenIds != null) {
            tokenIds.forEach(id -> redis.delete(tokenKey(id)));
        }
        redis.delete(userKey(userId));
    }

    private String tokenKey(String tokenId) {
        return "refresh:" + tokenId;
    }

    private String userKey(String userId) {
        return "user:" + userId + ":refresh";
    }
}
