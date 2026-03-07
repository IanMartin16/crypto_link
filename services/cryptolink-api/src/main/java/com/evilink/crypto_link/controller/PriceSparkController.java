package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.history.PriceHistoryCache;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1")
public class PriceSparkController {

    private final PriceHistoryCache historyCache;

    public PriceSparkController(PriceHistoryCache historyCache) {
        this.historyCache = historyCache;
    }

    @GetMapping("/prices/spark")
    public ResponseEntity<?> getPriceSpark(
        @RequestParam String symbols,
        @RequestParam(defaultValue = "MXN") String fiat
    ) {
        List<String> list = Arrays.stream(symbols.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .map(String::toUpperCase)
            .toList();

        Map<String, Object> out = new LinkedHashMap<>();
        for (String sym : list) {
            out.put(sym, historyCache.get(fiat, sym));
        }

        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofSeconds(10)).cachePublic())
            .body(Map.of(
                "ok", true,
                "fiat", fiat.toUpperCase(),
                "ts", OffsetDateTime.now().toString(),
                "source", "internal-history",
                "series", out
            ));
    }
}