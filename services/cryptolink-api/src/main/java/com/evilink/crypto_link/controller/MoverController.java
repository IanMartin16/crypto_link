package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.movers.MoverService;
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
public class MoverController {

    private final MoverService moverService;

    public MoverController(MoverService moverService) {
        this.moverService = moverService;
    }

    // ---- movers del rotate (TrendService), tal cual lo tenías ----
    @GetMapping("/movers")
    public ResponseEntity<?> getMovers(
        @RequestParam String symbols,
        @RequestParam(defaultValue = "USD") String fiat,
        @RequestParam(defaultValue = "5") int limit
    ) {
        List<String> list = Arrays.stream(symbols.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .map(String::toUpperCase)
            .toList();

        var result = moverService.getMovers(list, fiat, Math.max(1, Math.min(limit, 20)));

        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofSeconds(10)).cachePublic())
            .body(Map.of(
                "ok", true,
                "fiat", fiat.toUpperCase(),
                "ts", OffsetDateTime.now().toString(),
                "source", "internal-history",
                "gainers", result.gainers(),
                "losers", result.losers()
            ));
    }

    // ---- NUEVO: movers del UNIVERSO curado (~400), derivado de historical_prices ----
    //  GET /v1/top-movers?window=24h&fiat=USD&limit=10
    //  7d empieza a traer universo completo en cuanto los 50 nuevos cumplan la semana.
    @GetMapping("/top-movers")
    public ResponseEntity<?> topMovers(
        @RequestParam(defaultValue = "24h") String window,
        @RequestParam(defaultValue = "USD") String fiat,
        @RequestParam(defaultValue = "10") int limit
    ) {
        int hours = parseWindowHours(window);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        String fiatUp = fiat.toUpperCase();   // historical_prices guarda "USD" (FIAT del job)

        var result = moverService.getUniverseMovers(fiatUp, hours, safeLimit);

        // caché más largo: el job histórico corre por hora, no cada 50s
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
            .body(Map.of(
                "ok", true,
                "fiat", fiatUp,
                "window", window,
                "windowHours", hours,
                "ts", OffsetDateTime.now().toString(),
                "source", "internal-history-universe",
                "universeSize", result.universeSize(),
                "gainers", result.gainers(),
                "losers", result.losers()
            ));
    }

    /** "24h" -> 24, "7d" -> 168, número pelón = horas. Default 24h si no parsea. */
    private static int parseWindowHours(String window) {
        if (window == null || window.isBlank()) return 24;
        String w = window.trim().toLowerCase();
        try {
            if (w.endsWith("d")) return Integer.parseInt(w.substring(0, w.length() - 1)) * 24;
            if (w.endsWith("h")) return Integer.parseInt(w.substring(0, w.length() - 1));
            return Integer.parseInt(w);
        } catch (NumberFormatException e) {
            return 24;
        }
    }
}
