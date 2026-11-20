package tn.oliveconnect.resource;

/** A resource together with the reason it is free or unavailable for a requested slot. */
public record ResourceAvailabilityItemDto(
        String id,
        ResourceKind resourceKind,
        String resourceType,
        String code,
        String label,
        String firstName,
        String lastName,
        String status,
        boolean available,
        Double capacityKg,
        String reason) {
}
