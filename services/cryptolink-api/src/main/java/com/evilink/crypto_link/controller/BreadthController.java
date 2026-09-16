package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.breadth.BreadthService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import static java.util.Map.entry;

@RestController
@RequestMapping("/v1")
public class BreadthController {

    private final BreadthService breadthService;

    public BreadthController(BreadthService breadthService) {
        this.breadthService = breadthService;
    }

    // NO recibe symbols: breadth es una medida AGREGADA del universo (los 200),
    // no de una selección del usuario. Solo fiat.
    @GetMapping("/breadth")
    public ResponseEntity<?> getBreadth(
        @RequestParam(defaultValue = "USD") String fiat
    ) {
        var r = breadthService.getBreadth(fiat.toUpperCase());

        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
            .body(Map.ofEntries(
                entry("ok", true),
                entry("fiat", fiat.toUpperCase()),
                entry("ts", OffsetDateTime.now().toString()),
                entry("source", "historical-prices-db"),
                entry("pctAbove", r.pctAbove()),
                entry("above", r.above()),
                entry("below", r.below()),
                entry("neutral", r.neutral()),
                entry("excluded", r.excluded()),
                entry("movers", r.movers()),
                entry("maWindowPoints", r.maWindowPoints()),
                entry("reading", r.reading())
        ));

    }
}