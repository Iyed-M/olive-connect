package tn.oliveconnect.dashboard;

import java.time.Instant;
import java.util.List;
import tn.oliveconnect.dashboard.dto.FillRatePoint;
import tn.oliveconnect.dashboard.dto.KpiFilter;
import tn.oliveconnect.dashboard.dto.KpiSummaryDto;
import tn.oliveconnect.dashboard.dto.LeadTimePoint;
import tn.oliveconnect.dashboard.dto.MillVolumePoint;
import tn.oliveconnect.dashboard.dto.YieldPoint;

public interface KpiRepositoryCustom {

    KpiSummaryDto aggregateSummary(KpiFilter filter);

    List<YieldPoint> aggregateYield(KpiFilter filter, String groupBy);

    default List<YieldPoint> aggregateYield(KpiFilter filter) {
        return aggregateYield(filter, "orchard");
    }

    List<LeadTimePoint> aggregateLeadTime(KpiFilter filter, String groupBy);

    default List<LeadTimePoint> aggregateLeadTime(KpiFilter filter) {
        return aggregateLeadTime(filter, "delegation");
    }

    List<FillRatePoint> aggregateFillRate(KpiFilter filter, String groupBy);

    default List<FillRatePoint> aggregateFillRate(KpiFilter filter) {
        return aggregateFillRate(filter, "week");
    }

    List<MillVolumePoint> aggregateMillVolumes(KpiFilter filter);

    default List<FillRatePoint> fillRateByWeek(String season, Instant from, Instant to) {
        return aggregateFillRate(new KpiFilter(season, from, to, null, null, null), "week");
    }
}
