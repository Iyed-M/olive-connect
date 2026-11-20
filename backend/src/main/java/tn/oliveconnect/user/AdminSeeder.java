package tn.oliveconnect.user;

import java.util.EnumSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import tn.oliveconnect.config.AppProperties;

/** Creates the first Admin at startup, so someone can log in and create the other Users. */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;

    public AdminSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder, AppProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    /** Idempotent: does nothing when an Admin already exists (restarts must not create a second one). */
    public void seed() {
        if (userRepository.existsByRolesContaining(Role.ADMIN)) {
            return;
        }
        User admin = new User();
        admin.setEmail(properties.adminEmail().trim().toLowerCase());
        admin.setPasswordHash(passwordEncoder.encode(properties.adminPassword()));
        admin.setFirstName("Admin");
        admin.setLastName("OliveConnect");
        admin.setRoles(EnumSet.of(Role.ADMIN));
        userRepository.save(admin);   // createdBy is "system" because nobody is authenticated
        log.info("Seeded the first Admin: {}", admin.getEmail());
    }
}
