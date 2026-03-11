package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.health.MarketHealthService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1")
public class MarketHealthController {

    private final MarketHealthService marketHealthService;

    public MarketHealthController(MarketHealthService marketHealthService) {
        this.marketHealthService = marketHealthService;
    }

    @GetMapping("/market-health")
    public ResponseEntity<?> getMarketHealth(
        @RequestParam String symbols,
        @RequestParam(defaultValue = "MXN") String fiat
    ) {
        List<String> list = Arrays.stream(symbols.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .map(String::toUpperCase)
            .toList();

        var result = marketHealthService.getMarketHealth(list, fiat);

        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofSeconds(10)).cachePublic())
            .body(Map.of(
                "ok", true,
                "fiat", fiat.toUpperCase(),
                "ts", OffsetDateTime.now().toString(),
                "source", "internal-analysis",
                "marketHealth", result
            ));
    }
}