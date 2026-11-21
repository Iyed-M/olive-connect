package tn.oliveconnect.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tn.oliveconnect.notification.NotificationDto;
import tn.oliveconnect.notification.NotificationRepository;

@Component
public class RedisNotificationSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisNotificationSubscriber.class);

    private final ObjectMapper objectMapper;
    private final NotificationRepository notificationRepository;
    private final SseEmitterRegistry emitterRegistry;

    public RedisNotificationSubscriber(ObjectMapper objectMapper, NotificationRepository notificationRepository,
            SseEmitterRegistry emitterRegistry) {
        this.objectMapper = objectMapper;
        this.notificationRepository = notificationRepository;
        this.emitterRegistry = emitterRegistry;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            RedisNotificationEvent event = objectMapper.readValue(message.getBody(), RedisNotificationEvent.class);
            notificationRepository.findByIdAndRecipientId(event.id(), event.userId())
                    .map(NotificationDto::from)
                    .ifPresent(notification -> emitterRegistry.send(event.userId(), event.event(), notification));
        } catch (IOException | RuntimeException ex) {
            log.warn("Could not consume Redis notification event", ex);
        }
    }
}
