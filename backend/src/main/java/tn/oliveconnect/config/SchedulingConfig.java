package tn.oliveconnect.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.mongo.MongoLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables @Scheduled, with ShedLock storing its locks in the "shedLock" MongoDB collection. */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulingConfig {

    @Bean
    LockProvider lockProvider(MongoDatabaseFactory databaseFactory) {
        return new MongoLockProvider(databaseFactory.getMongoDatabase());
    }
}
