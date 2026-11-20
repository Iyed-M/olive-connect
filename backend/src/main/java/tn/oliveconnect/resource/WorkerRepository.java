package tn.oliveconnect.resource;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface WorkerRepository extends MongoRepository<Worker, String> {

    Optional<Worker> findByCin(String cin);

    List<Worker> findAllByExternalRefIn(Collection<String> externalRefs);
}
