package com.evilink.crypto_link.health.service;

import com.evilink.crypto_link.health.dto.HealthCheckResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class HealthService {

    private final String applicationVersion;
    private final String environment;

    public HealthService(
            @Value("${spring.application.version:unknown}")
            String applicationVersion,

            @Value("${spring.profiles.active:default}")
            String environment
    ) {
        this.applicationVersion = applicationVersion;
        this.environment = environment;
    }

    public Map<String, HealthCheckResponse> buildChecks() {
        Map<String, HealthCheckResponse> checks = new LinkedHashMap<>();

        checks.put(
                "application",
                HealthCheckResponse.operational()
        );

        checks.put(
                "configuration",
                configurationIsValid()
                        ? HealthCheckResponse.operational()
                        : HealthCheckResponse.degraded(
                                "Required application configuration is incomplete"
                        )
        );

        return checks;
    }

    public boolean isReady() {
        return configurationIsValid();
    }

    public long getUptimeSeconds() {
        return ManagementFactory
                .getRuntimeMXBean()
                .getUptime() / 1000;
    }

    public String getApplicationVersion() {
        return applicationVersion;
    }

    public String getEnvironment() {
        return environment;
    }

    private boolean configurationIsValid() {
        return applicationVersion != null
                && !applicationVersion.isBlank()
                && !"unknown".equalsIgnoreCase(applicationVersion)
                && environment != null
                && !environment.isBlank();
    }
}