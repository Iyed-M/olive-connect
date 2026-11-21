package tn.oliveconnect.resource;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface SupplyOrderRepository extends MongoRepository<SupplyOrder, String> {
}
