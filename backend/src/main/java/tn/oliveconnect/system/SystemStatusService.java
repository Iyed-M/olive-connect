package tn.oliveconnect.system;

import org.springframework.boot.info.BuildProperties;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

@Service
public class SystemStatusService {

    private final MongoTemplate mongoTemplate;
    private final RedisConnectionFactory redisConnectionFactory;
    private final BuildProperties buildProperties;

    public SystemStatusService(MongoTemplate mongoTemplate, RedisConnectionFactory redisConnectionFactory,
                               BuildProperties buildProperties) {
        this.mongoTemplate = mongoTemplate;
        this.redisConnectionFactory = redisConnectionFactory;
        this.buildProperties = buildProperties;
    }

    public SystemStatusDto getStatus() {
        return new SystemStatusDto("UP", mongoStatus(), redisStatus(), buildProperties.getVersion());
    }

    private String mongoStatus() {
        try {
            mongoTemplate.executeCommand("{ ping: 1 }");
            return "UP";
        } catch (Exception e) {
            return "DOWN";
        }
    }

    private String redisStatus() {
        // try-with-resources closes the connection after the ping
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.ping();
            return "UP";
        } catch (Exception e) {
            return "DOWN";
        }
    }
}
