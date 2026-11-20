package tn.oliveconnect.resource;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EquipmentRepository extends MongoRepository<Equipment, String> {

    Optional<Equipment> findByCode(String code);
}
