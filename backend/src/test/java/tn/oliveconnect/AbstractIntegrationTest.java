package tn.oliveconnect;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;

/**
 * Base class of the API tests. The containers are static and started once, then shared by every
 * test class (and by the Spring context Spring caches between them), which keeps the build fast.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    // "redis" name tells @ServiceConnection this generic container is Redis
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        mongo.start();
        redis.start();
    }

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // Rate-limit counters must not leak from one test to the next (all tests use the same IP).
    @BeforeEach
    void clearLoginRateLimits() {
        var keys = stringRedisTemplate.keys("ratelimit:login:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }
}
