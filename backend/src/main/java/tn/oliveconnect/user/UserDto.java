package tn.oliveconnect.user;

import java.time.Instant;
import java.util.Set;

/** A User as the Admin sees it. Never contains the password hash. */
public record UserDto(String id, String email, String firstName, String lastName, String phone, Set<Role> roles,
                      boolean enabled, FarmerProfileDto farmerProfile, Instant createdAt, Long version) {

    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(), user.getPhone(),
                user.getRoles(), user.isEnabled(), FarmerProfileDto.from(user.getFarmerProfile()),
                user.getCreatedAt(), user.getVersion());
    }
}
