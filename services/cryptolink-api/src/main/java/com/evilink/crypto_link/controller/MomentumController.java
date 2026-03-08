package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.momentum.MomentumService;
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
public class MomentumController {

    private final MomentumService momentumService;

    public MomentumController(MomentumService momentumService) {
        this.momentumService = momentumService;
    }

    @GetMapping("/momentum")
    public ResponseEntity<?> getMomentum(
        @RequestParam String symbols,
        @RequestParam(defaultValue = "MXN") String fiat
    ) {
        List<String> list = Arrays.stream(symbols.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .map(String::toUpperCase)
            .toList();

        var momentum = momentumService.getMomentum(list, fiat);

        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofSeconds(10)).cachePublic())
            .body(Map.of(
                "ok", true,
                "fiat", fiat.toUpperCase(),
                "ts", OffsetDateTime.now().toString(),
                "source", "internal-history",
                "momentum", momentum
            ));
    }
}