package tn.oliveconnect.round;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CollectionRoundRepository extends MongoRepository<CollectionRound, String> {

    Optional<CollectionRound> findByCode(String code);

    List<CollectionRound> findByDateBetween(LocalDate from, LocalDate to, Sort sort);

    List<CollectionRound> findByTeamLeaderIdAndDateBetween(String teamLeaderId, LocalDate from, LocalDate to,
                                                            Sort sort);

    List<CollectionRound> findByMillIdAndDateOrderByStartTimeAsc(String millId, LocalDate date);

    List<CollectionRound> findByStopsHarvestRequestId(String harvestRequestId);
}
