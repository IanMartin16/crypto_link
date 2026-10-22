package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.regime.RegimeService;
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
public class RegimeController {

    private final RegimeService regimeService;

    public RegimeController(RegimeService regimeService) {
        this.regimeService = regimeService;
    }

    @GetMapping("/regime")
    public ResponseEntity<?> getRegime(
        @RequestParam(required = false) String symbols,   // antes required: ahora opcional
        @RequestParam(defaultValue = "USD") String fiat
    ) {
        List<String> list = (symbols == null || symbols.isBlank())
            ? List.of()
            : Arrays.stream(symbols.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(String::toUpperCase)
                .toList();

        // SIN símbolos = universo curado = la vista honesta del mercado (default).
        // CON símbolos = set explícito (útil para vistas específicas / detector).
        boolean universe = list.isEmpty();
        var regime = universe
            ? regimeService.getUniverseRegime(fiat)
            : regimeService.getRegime(list, fiat);

        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofSeconds(10)).cachePublic())
            .body(Map.of(
                "ok", true,
                "fiat", fiat.toUpperCase(),
                "ts", OffsetDateTime.now().toString(),
                "source", "internal-analysis",
                "scope", universe ? "universe" : "custom",  // honesto: dice sobre qué se calculó
                "regime", regime
            ));
    }
}
