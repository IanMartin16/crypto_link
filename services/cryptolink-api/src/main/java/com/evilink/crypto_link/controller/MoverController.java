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

    @GetMapping("/movers")
    public ResponseEntity<?> getMovers(
        @RequestParam String symbols,
        @RequestParam(defaultValue = "MXN") String fiat,
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
}