package tn.oliveconnect.imports;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.TimeSeries;
import org.springframework.data.mongodb.core.timeseries.Granularity;

/** One weather measurement (docs/05 §4.10). Time-series collection: 3 seasons kept, then deleted by MongoDB. */
@Document("weather_readings")
@TimeSeries(timeField = "timestamp", metaField = "meta", granularity = Granularity.MINUTES, expireAfter = "1095d")
@CompoundIndex(name = "delegation_timestamp", def = "{ 'meta.delegation': 1, 'timestamp': -1 }")
public record WeatherReading(
        @Id String id,
        Instant timestamp,
        Meta meta,
        double temperatureC,
        double humidityPct,
        double rainfallMm,
        double windKmh) {

    /** The station and its delegation (a Delegation code such as SAKIET_EZZIT). */
    public record Meta(String stationId, String delegation) {
    }
}
