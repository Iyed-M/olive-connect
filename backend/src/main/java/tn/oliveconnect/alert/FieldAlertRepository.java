package tn.oliveconnect.alert;

import java.util.Collection;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FieldAlertRepository extends MongoRepository<FieldAlert, String> {

    /** Duplicate detection: one open alert of the same type per orchard. */
    boolean existsByOrchardIdAndTypeAndStatusIn(String orchardId, AlertType type, Collection<AlertStatus> statuses);
}
