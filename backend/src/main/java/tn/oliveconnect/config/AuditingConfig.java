package tn.oliveconnect.config;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Fills createdAt/createdBy/lastModifiedAt/lastModifiedBy on entities that declare those fields. */
@Configuration
@EnableMongoAuditing
public class AuditingConfig {

    @Bean
    AuditorAware<String> auditorAware() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            // the JWT subject is the user id; with no logged-in user (seed, startup) we write "system"
            if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
                return Optional.of(jwt.getSubject());
            }
            return Optional.of("system");
        };
    }
}
