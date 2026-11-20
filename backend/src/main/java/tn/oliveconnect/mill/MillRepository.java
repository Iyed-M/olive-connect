package tn.oliveconnect.mill;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MillRepository extends MongoRepository<Mill, String> {

    Optional<Mill> findByCode(String code);

    List<Mill> findAllByCodeIn(Collection<String> codes);
}
