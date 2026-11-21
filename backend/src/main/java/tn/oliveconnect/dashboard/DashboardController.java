package tn.oliveconnect.dashboard;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.oliveconnect.dashboard.dto.FillRatePoint;
import tn.oliveconnect.dashboard.dto.KpiSummaryDto;
import tn.oliveconnect.dashboard.dto.LeadTimePoint;
import tn.oliveconnect.dashboard.dto.MillVolumePoint;
import tn.oliveconnect.dashboard.dto.YieldPoint;

@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("isAuthenticated()")
public class DashboardController {

    private final KpiService kpiService;

    public DashboardController(KpiService kpiService) {
        this.kpiService = kpiService;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('COLLECTION_MANAGER', 'MILL_MANAGER', 'ADMIN')")
    public KpiSummaryDto summary(@RequestParam(required = false) String season,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(required = false) String variety, @RequestParam(required = false) String delegation,
            @RequestParam(required = false) String millId, Authentication authentication) {
        return kpiService.summary(query(season, from, to, variety, delegation, millId, null), authentication);
    }

    @GetMapping("/yield")
    @PreAuthorize("hasAnyRole('COLLECTION_MANAGER', 'ADMIN')")
    public List<YieldPoint> yield(@RequestParam(required = false) String season,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(required = false) String variety, @RequestParam(required = false) String delegation,
            @RequestParam(required = false) String millId, @RequestParam(required = false) String groupBy,
            Authentication authentication) {
        return kpiService.yield(query(season, from, to, variety, delegation, millId, groupBy), authentication);
    }

    @GetMapping("/lead-time")
    @PreAuthorize("hasAnyRole('COLLECTION_MANAGER', 'ADMIN')")
    public List<LeadTimePoint> leadTime(@RequestParam(required = false) String season,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(required = false) String variety, @RequestParam(required = false) String delegation,
            @RequestParam(required = false) String millId, @RequestParam(required = false) String groupBy,
            Authentication authentication) {
        return kpiService.leadTime(query(season, from, to, variety, delegation, millId, groupBy), authentication);
    }

    @GetMapping("/fill-rate")
    @PreAuthorize("hasAnyRole('COLLECTION_MANAGER', 'MILL_MANAGER', 'ADMIN')")
    public List<FillRatePoint> fillRate(@RequestParam(required = false) String season,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(required = false) String variety, @RequestParam(required = false) String delegation,
            @RequestParam(required = false) String millId, @RequestParam(required = false) String groupBy,
            Authentication authentication) {
        return kpiService.fillRate(query(season, from, to, variety, delegation, millId, groupBy), authentication);
    }

    @GetMapping("/mill-volumes")
    @PreAuthorize("hasAnyRole('COLLECTION_MANAGER', 'MILL_MANAGER', 'ADMIN')")
    public List<MillVolumePoint> millVolumes(@RequestParam(required = false) String season,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(required = false) String variety, @RequestParam(required = false) String delegation,
            @RequestParam(required = false) String millId, Authentication authentication) {
        return kpiService.millVolumes(query(season, from, to, variety, delegation, millId, null), authentication);
    }

    @GetMapping("/{kpi}/export.csv")
    @PreAuthorize("hasAnyRole('COLLECTION_MANAGER', 'MILL_MANAGER', 'ADMIN')")
    public ResponseEntity<byte[]> export(@PathVariable String kpi,
            @RequestParam(required = false) String season, @RequestParam(required = false) String from,
            @RequestParam(required = false) String to, @RequestParam(required = false) String variety,
            @RequestParam(required = false) String delegation, @RequestParam(required = false) String millId,
            @RequestParam(required = false) String groupBy, Authentication authentication) {
        CsvExport export = kpiService.export(kpi, query(season, from, to, variety, delegation, millId, groupBy),
                authentication);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(export.filename(), StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(export.content());
    }

    private KpiQuery query(String season, String from, String to, String variety, String delegation,
            String millId, String groupBy) {
        return new KpiQuery(season, from, to, variety, delegation, millId, groupBy);
    }
}
