package tn.oliveconnect.orchard;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrchardRepository extends MongoRepository<Orchard, String>, OrchardRepositoryCustom {

    List<Orchard> findAllByOwnerIdOrderByNameAsc(String ownerId);

    List<Orchard> findAllByExternalRefIn(Collection<String> externalRefs);

    Optional<Orchard> findByIdAndOwnerId(String id, String ownerId);
}
