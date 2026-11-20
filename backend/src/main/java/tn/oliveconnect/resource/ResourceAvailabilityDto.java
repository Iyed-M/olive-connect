package tn.oliveconnect.resource;

import java.util.List;

public record ResourceAvailabilityDto(List<ResourceAvailabilityItemDto> workers,
                                      List<ResourceAvailabilityItemDto> equipment) {
}
