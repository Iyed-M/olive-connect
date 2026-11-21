package tn.oliveconnect.event;

public record RedisNotificationEvent(String userId, String id, String event) {
}
