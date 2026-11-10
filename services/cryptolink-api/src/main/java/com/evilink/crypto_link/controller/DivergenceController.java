package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.divergence.DivergenceService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/v1")
public class DivergenceController {

    private final DivergenceService divergenceService;

    public DivergenceController(DivergenceService divergenceService) {
        this.divergenceService = divergenceService;
    }

    // Divergencia precio-vs-breadth sobre la ventana. Sin symbols: es de mercado.
    @GetMapping("/divergence")
    public ResponseEntity<?> getDivergence(
        @RequestParam(defaultValue = "USD") String fiat,
        @RequestParam(defaultValue = "7d") String window
    ) {
        String f = fiat.toUpperCase();
        var r = divergenceService.getDivergence(f, parseWindowHours(window));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("fiat", f);
        body.put("window", window);
        body.put("ts", OffsetDateTime.now().toString());
        body.put("base", 100);
        body.put("state", r.state());
        body.put("priceChangePct", r.priceChangePct());
        body.put("breadthChangePts", r.breadthChangePts());
        body.put("recentHours", r.recentHours());
        body.put("latestIndex", r.latestIndex());
        body.put("latestBreadth", r.latestBreadth());
        body.put("priceIndex", r.priceIndex());
        body.put("breadth", r.breadth());

        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
            .body(body);
    }

    private int parseWindowHours(String w) {
        if (w == null) return 168;
        return switch (w.trim().toLowerCase()) {
            case "3d" -> 72;
            case "7d" -> 168;
            case "14d" -> 336;
            case "30d" -> 720;
            default -> 168;
        };
    }
}
