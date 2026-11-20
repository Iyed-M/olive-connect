package tn.oliveconnect.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.IndexInfo;

class MongoIndexInitializerTest {

    @Test
    void recognizesTheLegacyOrchardGeoIndex() {
        IndexInfo legacy = IndexInfo.indexInfoOf(new Document("name", "location")
                .append("key", new Document("location", "2dsphere")));

        assertThat(MongoIndexInitializer.isLegacyOrchardGeoIndex(legacy)).isTrue();
    }
}
