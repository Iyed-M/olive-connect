package tn.oliveconnect.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

/** Stores field-alert photos in the dedicated GridFS bucket described by the schema. */
@Configuration
public class GridFsConfig {

    @Bean
    GridFsTemplate alertGridFsTemplate(MongoDatabaseFactory databaseFactory, MongoConverter converter) {
        return new GridFsTemplate(databaseFactory, converter, "alerts");
    }
}
