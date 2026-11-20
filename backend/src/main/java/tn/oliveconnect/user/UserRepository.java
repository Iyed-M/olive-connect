package tn.oliveconnect.user;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmail(String email);

    Optional<User> findByFarmerProfileMemberNumber(String memberNumber);

    boolean existsByRolesContaining(Role role);
}
