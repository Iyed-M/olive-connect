package tn.oliveconnect.settings;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** The single settings document of the platform (_id "global", docs/05 §4.11). */
@Document("system_settings")
public class SystemSettings {

    public static final String ID = "global";

    @Id
    private String id = ID;
    /** Rain over the last 24 hours (mm) that raises an automatic RAIN alert. */
    private double rainAlertMm24h = 20;
    private int defaultBinCapacityKg = 4000;

    public String getId() {
        return id;
    }

    public double getRainAlertMm24h() {
        return rainAlertMm24h;
    }

    public void setRainAlertMm24h(double rainAlertMm24h) {
        this.rainAlertMm24h = rainAlertMm24h;
    }

    public int getDefaultBinCapacityKg() {
        return defaultBinCapacityKg;
    }

    public void setDefaultBinCapacityKg(int defaultBinCapacityKg) {
        this.defaultBinCapacityKg = defaultBinCapacityKg;
    }
}
