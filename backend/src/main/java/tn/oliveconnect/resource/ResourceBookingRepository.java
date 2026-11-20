package tn.oliveconnect.resource;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface ResourceBookingRepository extends MongoRepository<ResourceBooking, String> {

    @Query("{ 'resourceId': { $in: ?0 }, 'status': 'ACTIVE', 'start': { $lt: ?2 }, 'end': { $gt: ?1 } }")
    List<ResourceBooking> findOverlapping(Collection<String> resourceIds, Instant start, Instant end);

    List<ResourceBooking> findByRoundId(String roundId);

    List<ResourceBooking> findByResourceIdAndStatus(String resourceId, BookingStatus status);
}
