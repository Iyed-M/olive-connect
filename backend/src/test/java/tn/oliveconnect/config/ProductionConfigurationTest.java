package tn.oliveconnect.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

class ProductionConfigurationTest {

    @Test
    void productionProfileReadsDeploymentEnvironment() throws IOException {
        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("deployment", Map.of(
                "MONGO_URI", "mongodb://app:secret@mongo:27017/oliveconnect?authSource=admin&replicaSet=rs0",
                "REDIS_HOST", "redis",
                "REDIS_PORT", "6380",
                "REDIS_PASSWORD", "redis-secret",
                "JWT_SECRET", "production-jwt-secret-that-is-long-enough",
                "ADMIN_EMAIL", "admin@example.tn",
                "ADMIN_PASSWORD", "production-admin-secret")));
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(
                "application-prod", new ClassPathResource("application-prod.yml"));
        sources.forEach(environment.getPropertySources()::addLast);

        assertThat(environment.getProperty("spring.data.mongodb.uri"))
                .isEqualTo("mongodb://app:secret@mongo:27017/oliveconnect?authSource=admin&replicaSet=rs0");
        assertThat(environment.getProperty("spring.data.redis.host")).isEqualTo("redis");
        assertThat(environment.getProperty("spring.data.redis.port")).isEqualTo("6380");
        assertThat(environment.getProperty("spring.data.redis.password")).isEqualTo("redis-secret");
        assertThat(environment.getProperty("app.jwt-secret")).isEqualTo("production-jwt-secret-that-is-long-enough");
        assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health,info,prometheus");
        assertThat(environment.getProperty("management.prometheus.metrics.export.enabled"))
                .isEqualTo("true");
    }

    @Test
    void redisConnectionFactoryUsesConfiguredPassword() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
                .withPropertyValues(
                        "spring.data.redis.host=redis.internal",
                        "spring.data.redis.port=6380",
                        "spring.data.redis.password=redis-secret")
                .run(context -> {
                    LettuceConnectionFactory factory = context.getBean(LettuceConnectionFactory.class);
                    RedisStandaloneConfiguration configuration = factory.getStandaloneConfiguration();

                    assertThat(configuration.getHostName()).isEqualTo("redis.internal");
                    assertThat(configuration.getPort()).isEqualTo(6380);
                    assertThat(new String(configuration.getPassword().get())).isEqualTo("redis-secret");
                });
    }
}
