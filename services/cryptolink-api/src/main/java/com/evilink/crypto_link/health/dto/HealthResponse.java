package com.evilink.crypto_link.health.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

public record HealthResponse(
        @JsonProperty("contract_version")
        String contractVersion,

        ServiceInfoResponse service,

        String status,

        String readiness,

        Instant timestamp,

        @JsonProperty("uptime_seconds")
        long uptimeSeconds,

        Map<String, HealthCheckResponse> checks
) {
}