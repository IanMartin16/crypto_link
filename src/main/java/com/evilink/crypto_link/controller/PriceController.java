package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.service.PriceService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@Validated
public class PriceController {

    private final PriceService priceService;

    public PriceController(PriceService priceService) {
        this.priceService = priceService;
    }

    @GetMapping("/v1/price")
    public Map<String, Object> price(
            @RequestParam(defaultValue = "BTC") String symbol,
            @RequestParam(defaultValue = "USD") String fiat
    ) {
        try {
            var r = priceService.getPrices(List.of(symbol), fiat);

            return Map.of(
                    "ok", true,
                    "symbol", symbol.toUpperCase(),
                    "fiat", r.fiat,
                    "price", r.prices.get(symbol.toUpperCase()),
                    "ts", r.ts,
                    "source", r.source
            );
        } catch (Exception e) {
            return Map.of("ok", false, "error", "Upstream provider error");
        }
    }
}
