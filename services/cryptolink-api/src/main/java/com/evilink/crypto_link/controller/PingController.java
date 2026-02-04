package com.evilink.crypto_link.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;


import java.time.OffsetDateTime;
import java.util.Map;

@RestController
public class PingController {

    @Operation(security = {})
    @GetMapping("/v1/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "ok", true,
                "service", "crypto-link",
                "ts", OffsetDateTime.now().toString()
        );
    }
}
