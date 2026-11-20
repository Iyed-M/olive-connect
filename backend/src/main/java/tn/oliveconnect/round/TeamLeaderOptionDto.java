package tn.oliveconnect.round;

import tn.oliveconnect.user.User;

public record TeamLeaderOptionDto(String id, String firstName, String lastName, boolean available) {

    public static TeamLeaderOptionDto from(User user) {
        return new TeamLeaderOptionDto(user.getId(), user.getFirstName(), user.getLastName(), true);
    }
}
