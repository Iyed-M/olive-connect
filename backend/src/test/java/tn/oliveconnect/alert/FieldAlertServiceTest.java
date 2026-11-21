package tn.oliveconnect.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.multipart.MultipartFile;
import tn.oliveconnect.common.ConflictException;
import tn.oliveconnect.harvest.HarvestRequestService;
import tn.oliveconnect.notification.NotificationService;
import tn.oliveconnect.orchard.GeoPoint;
import tn.oliveconnect.orchard.GeoPointDto;
import tn.oliveconnect.orchard.Orchard;
import tn.oliveconnect.orchard.OrchardRepository;

@ExtendWith(MockitoExtension.class)
class FieldAlertServiceTest {

    @Mock
    private FieldAlertRepository alertRepository;
    @Mock
    private OrchardRepository orchardRepository;
    @Mock
    private GridFsTemplate gridFsTemplate;
    @Mock
    private NotificationService notificationService;
    @Mock
    private HarvestRequestService harvestRequestService;

    private FieldAlertService service;
    private Authentication farmer;
    private Orchard orchard;

    @BeforeEach
    void setUp() {
        service = new FieldAlertService(alertRepository, orchardRepository, gridFsTemplate,
                notificationService, harvestRequestService);
        farmer = new TestingAuthenticationToken("farmer-1", "password",
                List.of(new SimpleGrantedAuthority("ROLE_FARMER")));
        orchard = new Orchard();
        orchard.setName("North orchard");
        orchard.setOwnerId("farmer-1");
        orchard.setLocation(new GeoPoint(10.7603, 34.7998));
        when(orchardRepository.findByIdAndOwnerId("orchard-1", "farmer-1"))
                .thenReturn(Optional.of(orchard));
    }

    @Test
    void reportFarFromOrchardKeepsAlertAndFlagsLocationMismatch() {
        when(alertRepository.save(any(FieldAlert.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        AlertReportDto report = new AlertReportDto(AlertType.MATURITY, "orchard-1",
                new GeoPointDto("Point", List.of(10.815, 34.7998)), "Olives are changing colour", null);

        FieldAlertDto result = service.report(report, List.of(), farmer);

        assertThat(result.locationMismatch()).isTrue();
        assertThat(result.status()).isEqualTo(AlertStatus.NEW);
        assertThat(result.source()).isEqualTo(AlertSource.FARMER);
        verify(alertRepository).save(any(FieldAlert.class));
    }

    @Test
    void reportWithOpenAlertOfSameTypeIsRejected() {
        when(alertRepository.existsByOrchardIdAndTypeAndStatusIn(any(), any(), any())).thenReturn(true);
        AlertReportDto report = new AlertReportDto(AlertType.MATURITY, "orchard-1",
                new GeoPointDto("Point", List.of(10.7603, 34.7998)), null, null);

        assertThatThrownBy(() -> service.report(report, List.<MultipartFile>of(), farmer))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("open");

        verify(alertRepository, never()).save(any(FieldAlert.class));
    }

    @Test
    void duplicateKeyFromOpenAlertIndexIsReturnedAsConflict() {
        when(alertRepository.save(any(FieldAlert.class)))
                .thenThrow(new DuplicateKeyException("open_alert_key"));
        AlertReportDto report = new AlertReportDto(AlertType.MATURITY, "orchard-1",
                new GeoPointDto("Point", List.of(10.7603, 34.7998)), null, null);

        assertThatThrownBy(() -> service.report(report, List.<MultipartFile>of(), farmer))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("open");
    }
}
