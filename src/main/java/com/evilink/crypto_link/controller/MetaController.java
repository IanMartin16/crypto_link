package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.validation.MarketValidator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;


import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestController
public class MetaController {

    private final MarketValidator validator;

    public MetaController(MarketValidator validator) {
        this.validator = validator;
    }

    @Operation(security = {})
    @GetMapping("/v1/symbols")
    public Map<String, Object> symbols() {
        return Map.of(
                "ok", true,
                "symbols", validator.allowedSymbols(),
                "ts", OffsetDateTime.now().toString()
        );
    }

    @Operation(security = {})
    @GetMapping("/v1/fiats")
    public Map<String, Object> fiats() {
        return Map.of(
                "ok", true,
                "fiats", validator.allowedFiats(),
                "ts", OffsetDateTime.now().toString()
        );
    }

    @Operation(security = {})
    @GetMapping("/v1/meta")
    public Map<String, Object> meta() {
        return Map.of(
                "ok", true,
                "name", "crypto_link",
                "version", "v1",
                "symbols", validator.allowedSymbols(),
                "fiats", validator.allowedFiats(),
                "endpoints", List.of(
                        "/v1/price",
                        "/v1/prices",
                        "/v1/stream/prices",
                        "/v1/auth/sse-token",
                        "/v1/symbols",
                        "/v1/fiats",
                        "/v1/meta"
                ),
                "ts", OffsetDateTime.now().toString()
        );
    }
}
