package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.security.ApiKeyFilter;
import com.evilink.crypto_link.security.ApiKeyStore;
import com.evilink.crypto_link.sse.PriceBroadcaster;
import com.evilink.crypto_link.validation.MarketValidator;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.Operation;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class StreamController {

    private final PriceBroadcaster broadcaster;
    private final MarketValidator validator;

    public StreamController(PriceBroadcaster broadcaster, MarketValidator validator) {
        this.broadcaster = broadcaster;
        this.validator = validator;
    }

    @SecurityRequirement(name = "apiKeyAuth")
    @Operation(
      summary = "Streaming SSE de precios",
      description = """
      Regresa eventos Server-Sent Events.
      - Puedes autenticar con header x-api-key
      - O con token en query param (?token=...)
      Eventos:
      - hello (al conectar)
      - price (cuando hay precios)
      - ping (keepalive)
      """
    )

    @SecurityRequirement(name = "apiKeyAuth")
    @GetMapping(value = "/v1/stream/prices", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPrices(
            HttpServletRequest req,
            @Parameter(description = "CSV de simbolos, ej: BTC,ETH") @RequestParam(defaultValue = "BTC,ETH") String symbols,
            @Parameter(description = "Fiat, ej: USD/MXN/EUR") @RequestParam(defaultValue = "USD") String fiat,
            @Parameter(description = "Token SSE (alternativa a x-api-key)") @RequestParam(required = false) String token
    ) throws Exception {

        String apiKey = (String) req.getAttribute(ApiKeyFilter.REQ_ATTR_API_KEY);
        ApiKeyStore.Plan plan = (ApiKeyStore.Plan) req.getAttribute(ApiKeyFilter.REQ_ATTR_PLAN);

        if(apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        var list = validator.normalizeSymbolsCsv(symbols);
        if (plan != null && list.size() > plan.maxSymbols) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Too many symbols. Max " + plan.maxSymbols + " for plan " + plan.name()
            );
        }
        var f = validator.normalizeFiat(fiat);
        return broadcaster.subscribe(apiKey, list, f);
    }
}
