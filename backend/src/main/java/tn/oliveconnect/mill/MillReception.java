package tn.oliveconnect.mill;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("mill_receptions")
@CompoundIndex(name = "season_received_at", def = "{ 'season': 1, 'receivedAt': 1 }")
@CompoundIndex(name = "season_orchard", def = "{ 'season': 1, 'orchardId': 1 }")
@CompoundIndex(name = "mill_received_at", def = "{ 'millId': 1, 'receivedAt': -1 }")
@CompoundIndex(name = "farmer_received_at", def = "{ 'farmerId': 1, 'receivedAt': -1 }")
@CompoundIndex(name = "round_bin_orchard_unique", def = "{ 'roundId': 1, 'binId': 1, 'orchardId': 1 }", unique = true)
public class MillReception {

    @Id
    private String id;
    private String millId;
    private String roundId;
    private String binId;
    private String orchardId;
    private String farmerId;
    private String harvestRequestId;
    private double grossWeightKg;
    private double tareKg;
    private double netWeightKg;
    private double binCapacityKg;
    private double fillRate;
    private QualityAssessment quality;
    private String season;
    private String varietyCode;
    private String delegation;
    private double orchardAreaHa;
    private Instant requestValidatedAt;
    private double leadTimeHours;
    private Instant receivedAt;
    private String receivedById;

    public String getId() { return id; }
    public String getMillId() { return millId; }
    public void setMillId(String millId) { this.millId = millId; }
    public String getRoundId() { return roundId; }
    public void setRoundId(String roundId) { this.roundId = roundId; }
    public String getBinId() { return binId; }
    public void setBinId(String binId) { this.binId = binId; }
    public String getOrchardId() { return orchardId; }
    public void setOrchardId(String orchardId) { this.orchardId = orchardId; }
    public String getFarmerId() { return farmerId; }
    public void setFarmerId(String farmerId) { this.farmerId = farmerId; }
    public String getHarvestRequestId() { return harvestRequestId; }
    public void setHarvestRequestId(String harvestRequestId) { this.harvestRequestId = harvestRequestId; }
    public double getGrossWeightKg() { return grossWeightKg; }
    public void setGrossWeightKg(double grossWeightKg) { this.grossWeightKg = grossWeightKg; }
    public double getTareKg() { return tareKg; }
    public void setTareKg(double tareKg) { this.tareKg = tareKg; }
    public double getNetWeightKg() { return netWeightKg; }
    public double getBinCapacityKg() { return binCapacityKg; }
    public void setBinCapacityKg(double binCapacityKg) { this.binCapacityKg = binCapacityKg; }
    public double getFillRate() { return fillRate; }
    public QualityAssessment getQuality() { return quality; }
    public void setQuality(QualityAssessment quality) { this.quality = quality; }
    public String getSeason() { return season; }
    public void setSeason(String season) { this.season = season; }
    public String getVarietyCode() { return varietyCode; }
    public void setVarietyCode(String varietyCode) { this.varietyCode = varietyCode; }
    public String getDelegation() { return delegation; }
    public void setDelegation(String delegation) { this.delegation = delegation; }
    public double getOrchardAreaHa() { return orchardAreaHa; }
    public void setOrchardAreaHa(double orchardAreaHa) { this.orchardAreaHa = orchardAreaHa; }
    public Instant getRequestValidatedAt() { return requestValidatedAt; }
    public void setRequestValidatedAt(Instant requestValidatedAt) { this.requestValidatedAt = requestValidatedAt; }
    public double getLeadTimeHours() { return leadTimeHours; }
    public void setLeadTimeHours(double leadTimeHours) { this.leadTimeHours = leadTimeHours; }
    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }
    public String getReceivedById() { return receivedById; }
    public void setReceivedById(String receivedById) { this.receivedById = receivedById; }

    public void computeNetAndFillRate() {
        netWeightKg = grossWeightKg - tareKg;
        fillRate = netWeightKg / binCapacityKg;
    }
}
