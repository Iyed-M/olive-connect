package tn.oliveconnect.imports;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.TimeSeries;
import org.springframework.data.mongodb.core.timeseries.Granularity;

/** One maturity measurement of an orchard (docs/05 §4.10). Time-series collection, kept forever. */
@Document("maturity_readings")
@TimeSeries(timeField = "timestamp", metaField = "meta", granularity = Granularity.HOURS)
@CompoundIndex(name = "orchard_timestamp", def = "{ 'meta.orchardId': 1, 'timestamp': -1 }")
public record MaturityReading(
        @Id String id,
        Instant timestamp,
        Meta meta,
        double maturityIndex,
        String varietyCode) {

    /** orchardId is the id of the Orchard document; source is SENSOR or MANUAL. */
    public record Meta(String orchardId, String source) {
    }
}
