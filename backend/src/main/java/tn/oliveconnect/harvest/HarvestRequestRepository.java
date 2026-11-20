package tn.oliveconnect.harvest;

import java.util.Collection;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface HarvestRequestRepository extends MongoRepository<HarvestRequest, String> {

    Optional<HarvestRequest> findByIdAndFarmerId(String id, String farmerId);

    boolean existsByOrchardIdAndStatusIn(String orchardId, Collection<HarvestRequestStatus> statuses);

}
