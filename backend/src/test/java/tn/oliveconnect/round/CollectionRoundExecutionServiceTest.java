package tn.oliveconnect.round;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import tn.oliveconnect.common.BusinessRuleException;
import tn.oliveconnect.common.ConflictException;
import tn.oliveconnect.common.FieldValidationException;
import tn.oliveconnect.harvest.HarvestRequestRepository;
import tn.oliveconnect.mill.MillRepository;
import tn.oliveconnect.notification.NotificationService;
import tn.oliveconnect.orchard.OrchardRepository;
import tn.oliveconnect.resource.BookingService;
import tn.oliveconnect.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class CollectionRoundExecutionServiceTest {

    @Mock CollectionRoundRepository roundRepository;
    @Mock HarvestRequestRepository requestRepository;
    @Mock UserRepository userRepository;
    @Mock MillRepository millRepository;
    @Mock BookingService bookingService;
    @Mock NotificationService notificationService;
    @Mock MongoTemplate mongoTemplate;
    @Mock OrchardRepository orchardRepository;

    @Test
    void startRejectsADifferentTeamLeader() {
        CollectionRound round = round(RoundStatus.PLANNED, LocalDate.now());
        round.setTeamLeaderId("assigned-leader");
        when(roundRepository.findById("round-id")).thenReturn(Optional.of(round));

        assertThatThrownBy(() -> service().start("round-id", authentication("other-leader")))
                .isInstanceOf(AccessDeniedException.class);
        verify(roundRepository, never()).save(any());
    }

    @Test
    void startRejectsARoundThatIsNotScheduledForToday() {
        CollectionRound round = round(RoundStatus.PLANNED, LocalDate.now().plusDays(1));
        round.setTeamLeaderId("assigned-leader");
        when(roundRepository.findById("round-id")).thenReturn(Optional.of(round));

        assertThatThrownBy(() -> service().start("round-id", authentication("assigned-leader")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("date");
        verify(roundRepository, never()).save(any());
    }

    @Test
    void doneRequiresHarvestQuantityAndCrates() {
        CollectionRound round = round(RoundStatus.IN_PROGRESS, LocalDate.now());
        round.setTeamLeaderId("assigned-leader");
        RoundStop stop = new RoundStop();
        stop.setOrder(1);
        stop.setStatus(StopStatus.IN_PROGRESS);
        round.setStops(List.of(stop));
        when(roundRepository.findById("round-id")).thenReturn(Optional.of(round));

        assertThatThrownBy(() -> service().updateStop("round-id", 1,
                new RoundStopUpdateDto(StopStatus.DONE, null, 10, null, null),
                authentication("assigned-leader")))
                .isInstanceOf(FieldValidationException.class)
                .extracting(throwable -> ((FieldValidationException) throwable).getField())
                .isEqualTo("harvestedKg");
        verify(roundRepository, never()).save(any());
    }

    @Test
    void skippedRequiresAReason() {
        CollectionRound round = round(RoundStatus.IN_PROGRESS, LocalDate.now());
        round.setTeamLeaderId("assigned-leader");
        RoundStop stop = new RoundStop();
        stop.setOrder(1);
        stop.setStatus(StopStatus.PENDING);
        round.setStops(List.of(stop));
        when(roundRepository.findById("round-id")).thenReturn(Optional.of(round));

        assertThatThrownBy(() -> service().updateStop("round-id", 1,
                new RoundStopUpdateDto(StopStatus.SKIPPED, null, null, "  ", null),
                authentication("assigned-leader")))
                .isInstanceOf(FieldValidationException.class)
                .extracting(throwable -> ((FieldValidationException) throwable).getField())
                .isEqualTo("comment");
        verify(roundRepository, never()).save(any());
    }

    @Test
    void completeRejectsPendingOrInProgressStopsWithoutSaving() {
        CollectionRound round = round(RoundStatus.IN_PROGRESS, LocalDate.now());
        round.setTeamLeaderId("assigned-leader");
        RoundStop pending = new RoundStop();
        pending.setOrder(1);
        pending.setStatus(StopStatus.PENDING);
        round.setStops(List.of(pending));
        when(roundRepository.findById("round-id")).thenReturn(Optional.of(round));

        assertThatThrownBy(() -> service().complete("round-id", authentication("assigned-leader")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not updated");
        verify(roundRepository, never()).save(any());
    }

    private CollectionRoundService service() {
        return new CollectionRoundService(roundRepository, requestRepository, userRepository, millRepository,
                bookingService, notificationService, mongoTemplate, orchardRepository);
    }

    private CollectionRound round(RoundStatus status, LocalDate date) {
        CollectionRound round = new CollectionRound();
        round.setStatus(status);
        round.setDate(date);
        return round;
    }

    private Authentication authentication(String userId) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(userId);
        org.mockito.Mockito.doReturn(List.of(new SimpleGrantedAuthority("ROLE_TEAM_LEADER")))
                .when(authentication).getAuthorities();
        return authentication;
    }
}
