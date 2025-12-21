package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.security.ApiKeyFilter;
import com.evilink.crypto_link.sse.PriceBroadcaster;
import com.evilink.crypto_link.validation.MarketValidator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class StreamController {

    private final PriceBroadcaster broadcaster;
    private final MarketValidator validator;

    public StreamController(PriceBroadcaster broadcaster, MarketValidator validator) {
        this.broadcaster = broadcaster;
        this.validator = validator;
    }

    @GetMapping(value = "/v1/stream/prices", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPrices(
            HttpServletRequest req,
            @RequestParam(defaultValue = "BTC,ETH") String symbols,
            @RequestParam(defaultValue = "USD") String fiat
    ) throws Exception {

        String apiKey = (String) req.getAttribute(ApiKeyFilter.REQ_ATTR_API_KEY);

        var list = validator.normalizeSymbols(symbols);
        var f = validator.normalizeFiat(fiat);

        return broadcaster.subscribe(apiKey, list, f);
    }
}
