package tn.oliveconnect.mill;

import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import tn.oliveconnect.orchard.GeoPoint;

/** Seeds stable reference mills; repeated startup runs never create duplicates. */
@Component
public class MillSeeder implements ApplicationRunner {

    private static final List<MillSeed> DEFAULTS = List.of(
            new MillSeed("Sfax North Olive Mill", "Route de Tunis, Sfax", 40_000, 10.7603, 34.7998),
            new MillSeed("Sfax South Olive Mill", "Route de Gabes, Sfax", 35_000, 10.7600, 34.7000),
            new MillSeed("Sakiet Ezzit Cooperative Mill", "Sakiet Ezzit, Sfax", 25_000, 10.7500, 34.8200));

    private final MillRepository repository;

    public MillSeeder(MillRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    public void seed() {
        for (MillSeed seed : DEFAULTS) {
            if (repository.findByName(seed.name()).isPresent()) {
                continue;
            }
            Mill mill = new Mill();
            mill.setName(seed.name());
            mill.setAddress(seed.address());
            mill.setDailyCapacityKg(seed.capacityKg());
            mill.setAvailableCapacityKg(seed.capacityKg());
            mill.setLocation(new GeoPoint(seed.longitude(), seed.latitude()));
            repository.save(mill);
        }
    }

    private record MillSeed(String name, String address, double capacityKg, double longitude, double latitude) {
    }
}
