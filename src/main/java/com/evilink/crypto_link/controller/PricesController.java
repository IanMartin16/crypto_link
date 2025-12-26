package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.service.PriceService;
import com.evilink.crypto_link.validation.MarketValidator;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class PricesController {

    private final PriceService priceService;
    private final MarketValidator validator;

    public PricesController(PriceService priceService, MarketValidator validator) {
        this.priceService = priceService;
        this.validator = validator;
    }

    @GetMapping("/v1/prices")
    public Map<String, Object> prices(
            @RequestParam(defaultValue = "BTC,ETH,SOL,XRP,ADA,DOGE") String symbols,
            @RequestParam(defaultValue = "USD") String fiat
    ) {
        var list = validator.normalizeSymbols(symbols);
        var f = validator.normalizeFiat(fiat);

        var r = priceService.getPrices(list, f);

        return Map.of(
                "ok", true,
                "fiat", r.fiat,
                "ts", r.ts,
                "source", r.source,
                "prices", r.prices
        );
    }
}
