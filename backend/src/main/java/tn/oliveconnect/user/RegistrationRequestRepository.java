package tn.oliveconnect.user;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RegistrationRequestRepository extends MongoRepository<RegistrationRequest, String> {

    List<RegistrationRequest> findByStatus(RegistrationStatus status, Sort sort);

    boolean existsByEmailAndStatus(String email, RegistrationStatus status);
}
