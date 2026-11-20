package tn.oliveconnect.harvest;

import tn.oliveconnect.orchard.GeoPoint;

/** The orchard values needed to keep a request readable after the orchard changes. */
public class OrchardSnapshot {

    private String id;
    private String name;
    private GeoPoint location;
    private String delegation;
    private String mainVarietyCode;
    private double areaHa;
    private double lastSeasonYieldKgHa;
    private double currentMaturityIndex;

    public OrchardSnapshot() {
    }

    public OrchardSnapshot(String id, String name, GeoPoint location, String delegation, String mainVarietyCode,
                           double areaHa, double lastSeasonYieldKgHa, double currentMaturityIndex) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.delegation = delegation;
        this.mainVarietyCode = mainVarietyCode;
        this.areaHa = areaHa;
        this.lastSeasonYieldKgHa = lastSeasonYieldKgHa;
        this.currentMaturityIndex = currentMaturityIndex;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public GeoPoint getLocation() {
        return location;
    }

    public String getDelegation() {
        return delegation;
    }

    public String getMainVarietyCode() {
        return mainVarietyCode;
    }

    public double getAreaHa() {
        return areaHa;
    }

    public double getLastSeasonYieldKgHa() {
        return lastSeasonYieldKgHa;
    }

    public double getCurrentMaturityIndex() {
        return currentMaturityIndex;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setLocation(GeoPoint location) {
        this.location = location;
    }

    public void setDelegation(String delegation) {
        this.delegation = delegation;
    }

    public void setMainVarietyCode(String mainVarietyCode) {
        this.mainVarietyCode = mainVarietyCode;
    }

    public void setAreaHa(double areaHa) {
        this.areaHa = areaHa;
    }

    public void setLastSeasonYieldKgHa(double lastSeasonYieldKgHa) {
        this.lastSeasonYieldKgHa = lastSeasonYieldKgHa;
    }

    public void setCurrentMaturityIndex(double currentMaturityIndex) {
        this.currentMaturityIndex = currentMaturityIndex;
    }
}
