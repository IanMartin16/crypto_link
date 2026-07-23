package com.evilink.crypto_link.health.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record LiveResponse(
        @JsonProperty("contract_version")
        String contractVersion,

        @JsonProperty("service_id")
        String serviceId,

        String status,

        Instant timestamp
) {
}