package com.evilink.crypto_link.history;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/v1")
public class TokenizedYieldsController {
    private final TokenizedYieldsService service;
    public TokenizedYieldsController(TokenizedYieldsService s) { this.service = s; }

    @GetMapping("/tokenized-yields")
    public Map<String,Object> get() {
        return Map.of(
            "ok", true,
            "method", "Implied APY · NAV accrual · not issuer-reported",
            "items", service.getYields()
        );
    }
}