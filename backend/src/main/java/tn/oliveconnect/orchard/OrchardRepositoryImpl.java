package tn.oliveconnect.orchard;

import java.util.Optional;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.NearQuery;
import org.springframework.data.mongodb.core.query.Query;

class OrchardRepositoryImpl implements OrchardRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    OrchardRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<Orchard> findNearestByOwnerId(String ownerId, double longitude, double latitude) {
        NearQuery query = NearQuery.near(new Point(longitude, latitude), Metrics.KILOMETERS)
                .spherical(true)
                .query(Query.query(Criteria.where("ownerId").is(ownerId)))
                .limit(1);
        GeoResults<Orchard> results = mongoTemplate.geoNear(query, Orchard.class);
        return results.getContent().stream()
                .findFirst()
                .map(result -> result.getContent());
    }
}
