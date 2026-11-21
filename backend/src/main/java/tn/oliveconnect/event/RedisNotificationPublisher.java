package tn.oliveconnect.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tn.oliveconnect.notification.Notification;

@Component
public class RedisNotificationPublisher {

    public static final String CHANNEL = "notifications";
    private static final Logger log = LoggerFactory.getLogger(RedisNotificationPublisher.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisNotificationPublisher(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void publish(Notification notification) {
        String eventName = notification.getType() != null && notification.getType().name().contains("ALERT")
                || notification.getType() == tn.oliveconnect.notification.NotificationType.NEW_ALERT
                ? "alert" : "notification";
        RedisNotificationEvent event = new RedisNotificationEvent(notification.getRecipientId(),
                notification.getId(), eventName);
        try {
            redis.convertAndSend(CHANNEL, objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException | RuntimeException ex) {
            // MongoDB is the source of truth; a Redis outage must not discard the notification.
            log.warn("Could not publish notification {} to Redis", notification.getId(), ex);
        }
    }
}
