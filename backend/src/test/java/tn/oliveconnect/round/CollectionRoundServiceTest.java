package tn.oliveconnect.round;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.Authentication;
import tn.oliveconnect.harvest.HarvestRequestRepository;
import tn.oliveconnect.mill.MillRepository;
import tn.oliveconnect.notification.NotificationService;
import tn.oliveconnect.resource.BookingService;
import tn.oliveconnect.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class CollectionRoundServiceTest {

    @Mock CollectionRoundRepository roundRepository;
    @Mock HarvestRequestRepository requestRepository;
    @Mock UserRepository userRepository;
    @Mock MillRepository millRepository;
    @Mock BookingService bookingService;
    @Mock NotificationService notificationService;
    @Mock MongoTemplate mongoTemplate;

    @Test
    void listCombinesFromAndToIntoOneDateCriterion() {
        when(mongoTemplate.find(any(Query.class), eq(CollectionRound.class))).thenReturn(List.of());
        CollectionRoundService service = newService();

        assertThat(service.list(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 20), RoundStatus.PLANNED,
                null, mock(Authentication.class))).isEmpty();

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(CollectionRound.class));
        Document dateCriteria = (Document) queryCaptor.getValue().getQueryObject().get("date");
        assertThat(dateCriteria).containsKeys("$gte", "$lte");
    }

    private CollectionRoundService newService() {
        return new CollectionRoundService(roundRepository, requestRepository, userRepository, millRepository,
                bookingService, notificationService, mongoTemplate);
    }
}
