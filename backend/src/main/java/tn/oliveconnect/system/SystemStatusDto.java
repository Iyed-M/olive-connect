package tn.oliveconnect.system;

public record SystemStatusDto(String api, String mongo, String redis, String version) {
}
