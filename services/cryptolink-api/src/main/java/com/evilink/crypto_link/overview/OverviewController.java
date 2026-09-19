package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.overview.OverviewService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/v1")
public class OverviewController {

    private final OverviewService overviewService;

    public OverviewController(OverviewService overviewService) {
        this.overviewService = overviewService;
    }

    // NO recibe symbols: es el top 100 del universo (como breadth). Solo fiat.
    @GetMapping("/overview")
    public ResponseEntity<?> getOverview(
        @RequestParam(defaultValue = "USD") String fiat
    ) {
        var items = overviewService.getOverview(fiat.toUpperCase());
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
            .body(Map.of(
                "ok", true,
                "fiat", fiat.toUpperCase(),
                "ts", OffsetDateTime.now().toString(),
                "source", "historical-prices-db",
                "count", items.size(),
                "items", items
            ));
    }
}