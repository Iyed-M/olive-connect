package tn.oliveconnect.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import tn.oliveconnect.AbstractIntegrationTest;

class AdminSeederTest extends AbstractIntegrationTest {

    @Autowired
    AdminSeeder adminSeeder;
    @Autowired
    UserRepository userRepository;
    @Autowired
    MongoTemplate mongoTemplate;

    @BeforeEach
    void cleanUsers() {
        userRepository.deleteAll();
    }

    @Test
    void seedingTwiceLeavesOneAdminCreatedBySystem() {
        adminSeeder.seed();
        adminSeeder.seed();

        List<User> users = userRepository.findAll();
        assertThat(users).hasSize(1);
        assertThat(users.get(0).getRoles()).containsExactly(Role.ADMIN);
        assertThat(users.get(0).getCreatedBy()).isEqualTo("system");
        assertThat(users.get(0).getCreatedAt()).isNotNull();
    }

    @Test
    void usersCollectionHasTheDocumentedIndexes() {
        List<String> keys = mongoTemplate.indexOps(User.class).getIndexInfo().stream()
                .map(IndexInfo::getName)
                .toList();
        assertThat(keys).contains("email", "roles_enabled", "farmerProfile.memberNumber");

        IndexInfo member = mongoTemplate.indexOps(User.class).getIndexInfo().stream()
                .filter(i -> i.getName().equals("farmerProfile.memberNumber")).findFirst().orElseThrow();
        assertThat(member.isUnique()).isTrue();
        assertThat(member.isSparse()).isTrue();
    }
}
