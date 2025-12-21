package com.evilink.crypto_link.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ApiMetrics {

    private final MeterRegistry registry;

    public ApiMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void incRequest(String path, String plan) {
        Counter.builder("cryptolink_requests_total")
                .tag("path", path)
                .tag("plan", plan)
                .register(registry)
                .increment();
    }

    public void incDenied(String reason) {
        Counter.builder("cryptolink_denied_total")
                .tag("reason", reason) // missing_key | invalid_key | rate_limit
                .register(registry)
                .increment();
    }

    public void incUpstreamError(String provider) {
        Counter.builder("cryptolink_upstream_errors_total")
                .tag("provider", provider)
                .register(registry)
                .increment();
    }
}
