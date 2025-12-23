package com.evilink.crypto_link.service;

import com.evilink.crypto_link.exception.UpstreamException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CoinGeckoPriceProvider {

    private final RestClient coingecko;

    private static final Map<String, String> SYMBOL_TO_ID = Map.of(
            "BTC", "bitcoin",
            "ETH", "ethereum"
    );

    public CoinGeckoPriceProvider(RestClient coingeckoRestClient) {
        this.coingecko = coingeckoRestClient;
    }

    @SuppressWarnings("unchecked")
    public Map<String, BigDecimal> getPrices(List<String> symbols, String fiat) {
        String vs = fiat.toLowerCase();
        String ids = symbols.stream()
                .map(String::toUpperCase)
                .map(SYMBOL_TO_ID::get)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        if (ids.isBlank()) return Map.of();

        Map<String, Object> resp;
        try {
            resp = coingecko.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/simple/price")
                            .queryParam("ids", ids)
                            .queryParam("vs_currencies", vs)
                            .build())
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException e) {
            // aquí caen 4xx/5xx del proveedor (incluye 429)
            throw new UpstreamException("CoinGecko HTTP " + e.getStatusCode().value(), e);
        } catch (Exception e) {
            // timeouts, DNS, conexión, parse raro, etc.
            throw new UpstreamException("CoinGecko request failed", e);
        }

        Map<String, BigDecimal> out = new HashMap<>();
        if (resp == null) return out;

        for (String sym : symbols) {
            String id = SYMBOL_TO_ID.get(sym.toUpperCase());
            if (id == null) continue;

            Object rowObj = resp.get(id);
            if (!(rowObj instanceof Map<?, ?> row)) continue;

            Object priceObj = row.get(vs);
            if (priceObj instanceof Number n) {
                out.put(sym.toUpperCase(), BigDecimal.valueOf(n.doubleValue()));
            }
        }
        return out;
    }
}
