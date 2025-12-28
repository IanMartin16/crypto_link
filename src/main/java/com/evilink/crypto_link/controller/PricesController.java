package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.security.ApiKeyFilter;
import com.evilink.crypto_link.security.ApiKeyStore;
import com.evilink.crypto_link.service.PriceService;
import com.evilink.crypto_link.validation.MarketValidator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

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
      HttpServletRequest req,
      @RequestParam(defaultValue = "BTC,ETH") String symbols,
      @RequestParam(defaultValue = "USD") String fiat
  ) {

    ApiKeyStore.Plan plan = (ApiKeyStore.Plan) req.getAttribute(ApiKeyFilter.REQ_ATTR_PLAN);

    List<String> list = validator.normalizeSymbolsCsv(symbols);
    if(plan != null && list.size() > plan.maxSymbols) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
        "Too many symbols. Max " + plan.maxSymbols + " for plan " + plan.name()
      );
    }
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
