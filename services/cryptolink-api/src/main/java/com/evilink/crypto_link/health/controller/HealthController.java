package com.evilink.crypto_link.health.controller;

import com.evilink.crypto_link.health.dto.HealthResponse;
import com.evilink.crypto_link.health.dto.LiveResponse;
import com.evilink.crypto_link.health.dto.ReadyResponse;
import com.evilink.crypto_link.health.dto.ServiceInfoResponse;
import com.evilink.crypto_link.health.service.HealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class HealthController {

    private static final String CONTRACT_VERSION = "health.v1";
    private static final String SERVICE_ID = "cryptolink";

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/api/health")
    public ResponseEntity<HealthResponse> health() {
        boolean ready = healthService.isReady();

        HealthResponse body = new HealthResponse(
                CONTRACT_VERSION,
                new ServiceInfoResponse(
                        SERVICE_ID,
                        "CryptoLink",
                        healthService.getApplicationVersion(),
                        healthService.getEnvironment(),
                        "spring-boot"
                ),
                ready ? "operational" : "degraded",
                ready ? "ready" : "not_ready",
                Instant.now(),
                healthService.getUptimeSeconds(),
                healthService.buildChecks()
        );

        return ready
                ? ResponseEntity.ok(body)
                : ResponseEntity.status(503).body(body);
    }

    @GetMapping("/api/health/live")
    public LiveResponse live() {
        return new LiveResponse(
                CONTRACT_VERSION,
                SERVICE_ID,
                "alive",
                Instant.now()
        );
    }

    @GetMapping("/api/health/ready")
    public ResponseEntity<ReadyResponse> ready() {
        boolean ready = healthService.isReady();

        ReadyResponse body = new ReadyResponse(
                CONTRACT_VERSION,
                SERVICE_ID,
                ready ? "ready" : "not_ready",
                Instant.now(),
                healthService.buildChecks()
        );

        return ready
                ? ResponseEntity.ok(body)
                : ResponseEntity.status(503).body(body);
    }
}