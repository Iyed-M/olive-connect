package tn.oliveconnect.mill;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MillReceptionRepository extends MongoRepository<MillReception, String> {

    boolean existsByRoundIdAndBinIdAndOrchardId(String roundId, String binId, String orchardId);

    List<MillReception> findByFarmerIdOrderByReceivedAtDesc(String farmerId);

    List<MillReception> findByMillIdOrderByReceivedAtDesc(String millId);

    List<MillReception> findAllByOrderByReceivedAtDesc();
}
