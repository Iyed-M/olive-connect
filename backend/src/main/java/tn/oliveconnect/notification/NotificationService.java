package tn.oliveconnect.notification;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tn.oliveconnect.common.NotFoundException;
import tn.oliveconnect.event.RedisNotificationPublisher;
import tn.oliveconnect.user.Role;
import tn.oliveconnect.user.User;
import tn.oliveconnect.user.UserRepository;

@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final RedisNotificationPublisher notificationPublisher;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository,
            RedisNotificationPublisher notificationPublisher) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.notificationPublisher = notificationPublisher;
    }

    public void notifyCollectionManagers(NotificationType type, String title, String message, String link) {
        userRepository.findAll().stream()
                .filter(User::isEnabled)
                .filter(user -> user.getRoles().contains(Role.COLLECTION_MANAGER))
                .forEach(user -> create(user.getId(), type, title, message, link));
    }

    public void notifyUser(String recipientId, NotificationType type, String title, String message, String link) {
        create(recipientId, type, title, message, link);
    }

    public List<NotificationDto> list(String recipientId, Boolean unread) {
        List<Notification> notifications = unread == null
                ? notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId)
                : notificationRepository.findByRecipientIdAndReadOrderByCreatedAtDesc(recipientId, !unread);
        return notifications.stream().map(NotificationDto::from).toList();
    }

    public NotificationDto markRead(String notificationId, String recipientId) {
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> new NotFoundException("Notification not found"));
        notification.setRead(true);
        return NotificationDto.from(notificationRepository.save(notification));
    }

    public void markAllRead(String recipientId) {
        List<Notification> unread = notificationRepository.findByRecipientIdAndReadOrderByCreatedAtDesc(recipientId, false);
        unread.forEach(notification -> notification.setRead(true));
        notificationRepository.saveAll(unread);
    }

    private void create(String recipientId, NotificationType type, String title, String message, String link) {
        Notification notification = new Notification();
        notification.setRecipientId(recipientId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setLink(link);
        Notification saved = notificationRepository.save(notification);
        publishAfterCommit(saved);
    }

    private void publishAfterCommit(Notification notification) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            notificationPublisher.publish(notification);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notificationPublisher.publish(notification);
            }
        });
    }
}
