package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.compare.CompareService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1")
public class CompareController {

    private final CompareService compareService;

    public CompareController(CompareService compareService) {
        this.compareService = compareService;
    }

    // Serie rebaseada (base 100) para los símbolos dados, estilo "compare".
    // symbols: lista separada por coma (el top-N del panel de momentum).
    @GetMapping("/compare")
    public ResponseEntity<?> getCompare(
        @RequestParam String symbols,
        @RequestParam(defaultValue = "7d") String window,
        @RequestParam(defaultValue = "USD") String fiat
    ) {
        List<String> syms = Arrays.stream(symbols.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(String::toUpperCase)
            .distinct()
            .limit(12)   // tope defensivo (el chart no debe comparar más de ~12)
            .collect(Collectors.toList());

        var result = compareService.getRebased(syms, fiat.toUpperCase(), parseWindowHours(window));

        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofSeconds(300)).cachePublic())
            .body(Map.of(
                "ok", true,
                "fiat", fiat.toUpperCase(),
                "window", window,
                "ts", OffsetDateTime.now().toString(),
                "base", 100,
                "series", result.series()
            ));
    }

    private int parseWindowHours(String w) {
        if (w == null) return 168;
        return switch (w.trim().toLowerCase()) {
            case "24h" -> 24;
            case "3d" -> 72;
            case "7d" -> 168;
            case "14d" -> 336;
            case "30d" -> 720;
            default -> 168;
        };
    }
}
