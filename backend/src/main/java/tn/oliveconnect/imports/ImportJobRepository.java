package tn.oliveconnect.imports;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ImportJobRepository extends MongoRepository<ImportJob, String> {

    Optional<ImportJob> findByChecksum(String checksum);
}
