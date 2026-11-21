package tn.oliveconnect.orchard;

import java.util.Optional;

public interface OrchardRepositoryCustom {

    Optional<Orchard> findNearestByOwnerId(String ownerId, double longitude, double latitude);
}
