package tn.oliveconnect.orchard;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrchardRepository extends MongoRepository<Orchard, String> {

    List<Orchard> findAllByOwnerIdOrderByNameAsc(String ownerId);

    Optional<Orchard> findByIdAndOwnerId(String id, String ownerId);
}
