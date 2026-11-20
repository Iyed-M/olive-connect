package tn.oliveconnect.config;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver.IndexDefinitionHolder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.stereotype.Component;
import tn.oliveconnect.imports.WeatherReading;
import tn.oliveconnect.orchard.Orchard;

/** Creates declared MongoDB indexes after reconciling the M2 orchard index name. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MongoIndexInitializer implements ApplicationRunner {

    static final String LEGACY_ORCHARD_GEO_INDEX = "location";

    private static final Logger log = LoggerFactory.getLogger(MongoIndexInitializer.class);

    private final MongoTemplate mongoTemplate;
    private final MongoMappingContext mappingContext;

    public MongoIndexInitializer(MongoTemplate mongoTemplate, MongoMappingContext mappingContext) {
        this.mongoTemplate = mongoTemplate;
        this.mappingContext = mappingContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        reconcileLegacyOrchardGeoIndex();
        createTimeSeriesCollections();

        MongoPersistentEntityIndexResolver resolver = new MongoPersistentEntityIndexResolver(mappingContext);
        for (MongoPersistentEntity<?> entity : mappingContext.getPersistentEntities()) {
            if (!entity.getType().isAnnotationPresent(Document.class)) {
                continue;
            }
            for (IndexDefinitionHolder definition : resolver.resolveIndexForEntity(entity)) {
                mongoTemplate.indexOps(definition.getCollection()).ensureIndex(definition);
            }
        }
    }

    /** Time-series collections cannot be created implicitly by an insert, so they are created first (R7, R12). */
    void createTimeSeriesCollections() {
        if (!mongoTemplate.collectionExists(WeatherReading.class)) {
            mongoTemplate.createCollection(WeatherReading.class);
        }
    }

    void reconcileLegacyOrchardGeoIndex() {
        IndexOperations indexOperations = mongoTemplate.indexOps(Orchard.class);
        List<IndexInfo> indexes = indexOperations.getIndexInfo();
        for (IndexInfo index : indexes) {
            if (isLegacyOrchardGeoIndex(index)) {
                indexOperations.dropIndex(index.getName());
                log.info("Dropped legacy orchard geospatial index {}; it will be recreated as location_2dsphere",
                        index.getName());
            }
        }
    }

    static boolean isLegacyOrchardGeoIndex(IndexInfo index) {
        return LEGACY_ORCHARD_GEO_INDEX.equals(index.getName())
                && index.getIndexFields().size() == 1
                && "location".equals(index.getIndexFields().get(0).getKey());
    }
}
