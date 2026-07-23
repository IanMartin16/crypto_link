package com.evilink.crypto_link.health.dto;

public record ServiceInfoResponse(
        String id,
        String name,
        String version,
        String environment,
        String stack
) {
}