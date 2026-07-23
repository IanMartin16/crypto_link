package com.evilink.crypto_link.health.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HealthCheckResponse(
        String status,
        String message
) {
    public static HealthCheckResponse operational() {
        return new HealthCheckResponse("operational", null);
    }

    public static HealthCheckResponse degraded(String message) {
        return new HealthCheckResponse("degraded", message);
    }
}