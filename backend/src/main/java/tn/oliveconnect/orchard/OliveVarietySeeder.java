package tn.oliveconnect.orchard;

import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Seeds the small, stable reference list once at startup. */
@Component
public class OliveVarietySeeder implements ApplicationRunner {

    private static final List<OliveVariety> DEFAULTS = List.of(
            new OliveVariety("CHEMLALI", "Chemlali", 20, 3.0, 4.5),
            new OliveVariety("CHETOUI", "Chetoui", 22, 2.8, 4.2),
            new OliveVariety("MESKI", "Meski", 21, 3.0, 4.4),
            new OliveVariety("OUESLATI", "Oueslati", 19, 3.1, 4.6),
            new OliveVariety("ZALEN", "Zalen", 20, 3.0, 4.5));

    private final OliveVarietyRepository repository;
    private final StringRedisTemplate redis;

    public OliveVarietySeeder(OliveVarietyRepository repository, StringRedisTemplate redis) {
        this.repository = repository;
        this.redis = redis;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    public void seed() {
        for (OliveVariety variety : DEFAULTS) {
            if (!repository.existsById(variety.getCode())) {
                repository.save(variety);
            }
        }
        redis.delete(OrchardService.VARIETY_CACHE_KEY);
    }
}
