package tn.oliveconnect.notification;

import java.time.Instant;

public record NotificationDto(
        String id,
        NotificationType type,
        String title,
        String message,
        String link,
        boolean read,
        Instant createdAt) {

    public static NotificationDto from(Notification notification) {
        return new NotificationDto(notification.getId(), notification.getType(), notification.getTitle(),
                notification.getMessage(), notification.getLink(), notification.isRead(), notification.getCreatedAt());
    }
}
