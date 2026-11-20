package tn.oliveconnect.user;

import java.util.Set;

/** What the frontend needs to know about the logged-in User. Never contains the password hash. */
public record MeDto(String id, String email, String firstName, String lastName, Set<Role> roles,
                    boolean onboardingDone) {

    public static MeDto from(User user) {
        return new MeDto(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getRoles(), user.isOnboardingDone());
    }
}
