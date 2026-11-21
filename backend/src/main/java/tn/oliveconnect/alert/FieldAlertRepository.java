package tn.oliveconnect.alert;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FieldAlertRepository extends MongoRepository<FieldAlert, String> {

    /** Duplicate detection: one open alert of the same type per orchard. */
    boolean existsByOrchardIdAndTypeAndStatusIn(String orchardId, AlertType type, Collection<AlertStatus> statuses);

    List<FieldAlert> findAllByOrderByCreatedAtDesc();

    List<FieldAlert> findByFarmerIdOrderByCreatedAtDesc(String farmerId);

    List<FieldAlert> findByHandledByIdOrderByCreatedAtDesc(String handledById);

    Optional<FieldAlert> findByIdAndFarmerId(String id, String farmerId);
}
