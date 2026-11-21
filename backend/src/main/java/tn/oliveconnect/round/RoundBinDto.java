package tn.oliveconnect.round;

import tn.oliveconnect.resource.Bin;

/** Bin details exposed with the mill's incoming rounds for the weighing form. */
public record RoundBinDto(String id, String code, double capacityKg) {

    public static RoundBinDto from(Bin bin) {
        return new RoundBinDto(bin.getId(), bin.getCode(), bin.getCapacityKg());
    }
}
