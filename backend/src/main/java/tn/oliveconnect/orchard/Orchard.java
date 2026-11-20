package tn.oliveconnect.orchard;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("orchards")
@CompoundIndex(name = "harvest_status_variety_delegation",
        def = "{ 'harvestStatus': 1, 'mainVarietyCode': 1, 'delegation': 1 }")
@CompoundIndex(name = "orchard_search_text", def = "{ 'name': 'text', 'owner.displayName': 'text' }")
public class Orchard {

    @Id
    private String id;
    private String name;
    // key of the source system (orchards.csv), null for orchards created in the app
    @Indexed(unique = true, sparse = true)
    private String externalRef;
    @Indexed
    private String ownerId;
    private Map<String, String> owner = new LinkedHashMap<>();
    @GeoSpatialIndexed(name = "location_2dsphere", type = GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoPoint location;
    private String delegation;
    private List<Plot> plots = new ArrayList<>();
    private double areaHa;
    private int treeCount;
    private String mainVarietyCode;
    private HarvestStatus harvestStatus = HarvestStatus.NOT_HARVESTED;
    private double currentMaturityIndex;
    private Instant maturityUpdatedAt;
    private double lastSeasonYieldKgHa;
    @Version
    private Long version;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public void setExternalRef(String externalRef) {
        this.externalRef = externalRef;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public Map<String, String> getOwner() {
        return owner;
    }

    public void setOwner(Map<String, String> owner) {
        this.owner = owner;
    }

    public GeoPoint getLocation() {
        return location;
    }

    public void setLocation(GeoPoint location) {
        this.location = location;
    }

    public String getDelegation() {
        return delegation;
    }

    public void setDelegation(String delegation) {
        this.delegation = delegation;
    }

    public List<Plot> getPlots() {
        return plots;
    }

    public void setPlots(List<Plot> plots) {
        this.plots = plots;
    }

    public double getAreaHa() {
        return areaHa;
    }

    public int getTreeCount() {
        return treeCount;
    }

    public String getMainVarietyCode() {
        return mainVarietyCode;
    }

    public HarvestStatus getHarvestStatus() {
        return harvestStatus;
    }

    public void setHarvestStatus(HarvestStatus harvestStatus) {
        this.harvestStatus = harvestStatus;
    }

    public double getCurrentMaturityIndex() {
        return currentMaturityIndex;
    }

    public void setCurrentMaturityIndex(double currentMaturityIndex) {
        this.currentMaturityIndex = currentMaturityIndex;
    }

    public Instant getMaturityUpdatedAt() {
        return maturityUpdatedAt;
    }

    public void setMaturityUpdatedAt(Instant maturityUpdatedAt) {
        this.maturityUpdatedAt = maturityUpdatedAt;
    }

    public double getLastSeasonYieldKgHa() {
        return lastSeasonYieldKgHa;
    }

    public void setLastSeasonYieldKgHa(double lastSeasonYieldKgHa) {
        this.lastSeasonYieldKgHa = lastSeasonYieldKgHa;
    }

    public Long getVersion() {
        return version;
    }

    /** Stores the derived totals so map and list reads do not recalculate them. */
    public void recomputeTotals() {
        areaHa = plots.stream().mapToDouble(Plot::getAreaHa).sum();
        treeCount = plots.stream().mapToInt(Plot::getTreeCount).sum();
        mainVarietyCode = plots.stream()
                .max((left, right) -> Double.compare(left.getAreaHa(), right.getAreaHa()))
                .map(Plot::getVarietyCode)
                .orElse(null);
    }
}
