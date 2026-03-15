package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.social.SocialPulseService;
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
public class SocialPulseController {

    private final SocialPulseService socialPulseService;

    public SocialPulseController(SocialPulseService socialPulseService) {
        this.socialPulseService = socialPulseService;
    }

    @GetMapping("/social-pulse")
    public ResponseEntity<?> getSocialPulse(
        @RequestParam String symbols,
        @RequestParam(defaultValue = "MXN") String fiat
    ) {
        List<String> list = Arrays.stream(symbols.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .map(String::toUpperCase)
            .toList();

        var result = socialPulseService.getSocialPulse(list, fiat);

        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofSeconds(10)).cachePublic())
            .body(Map.of(
                "ok", true,
                "fiat", fiat.toUpperCase(),
                "ts", OffsetDateTime.now().toString(),
                "source", "internal-social-derivation",
                "socialPulse", result
            ));
    }
}