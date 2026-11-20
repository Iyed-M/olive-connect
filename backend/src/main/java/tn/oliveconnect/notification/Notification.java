package tn.oliveconnect.notification;

import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("notifications")
@CompoundIndex(name = "recipient_read_created_at", def = "{ 'recipientId': 1, 'read': 1, 'createdAt': -1 }")
public class Notification {

    private static final int NINETY_DAYS_IN_SECONDS = 90 * 24 * 60 * 60;

    @Id
    private String id;
    private String recipientId;
    private NotificationType type;
    private String title;
    private String message;
    private String link;
    private boolean read;
    @CreatedDate
    @Indexed(name = "created_at_ttl", expireAfterSeconds = NINETY_DAYS_IN_SECONDS)
    private Instant createdAt;

    public String getId() {
        return id;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
