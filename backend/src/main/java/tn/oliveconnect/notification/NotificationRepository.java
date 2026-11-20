package tn.oliveconnect.notification;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(String recipientId);

    List<Notification> findByRecipientIdAndReadOrderByCreatedAtDesc(String recipientId, boolean read);

    Optional<Notification> findByIdAndRecipientId(String id, String recipientId);
}
