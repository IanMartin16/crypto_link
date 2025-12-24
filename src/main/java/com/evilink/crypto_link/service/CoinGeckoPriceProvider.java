package com.evilink.crypto_link.service;

import com.evilink.crypto_link.exception.UpstreamException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class CoinGeckoPriceProvider {

    private final RestClient coingecko;
    private final SymbolService symbolService;

    public CoinGeckoPriceProvider(RestClient coingeckoRestClient, SymbolService symbolService) {
        this.coingecko = coingeckoRestClient;
        this.symbolService = symbolService;
    }

    @SuppressWarnings("unchecked")
    public Map<String, BigDecimal> getPrices(List<String> symbols, String fiat) {
        String vs = fiat.toLowerCase();

        List<String> symsUpper = symbols.stream()
                .map(s -> s.trim().toUpperCase())
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        // DB: symbol -> coingecko_id
        Map<String, String> symToId = symbolService.resolveIds(symsUpper);
        if (symToId.isEmpty()) return Map.of();

        String ids = symToId.values().stream().distinct().collect(Collectors.joining(","));
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
            throw new UpstreamException("CoinGecko HTTP " + e.getStatusCode().value(), e);
        } catch (Exception e) {
            throw new UpstreamException("CoinGecko request failed", e);
        }

        Map<String, BigDecimal> out = new HashMap<>();
        if (resp == null) return out;

        for (String sym : symsUpper) {
            String id = symToId.get(sym);
            if (id == null) continue;

            Object rowObj = resp.get(id);
            if (!(rowObj instanceof Map<?, ?> row)) continue;

            Object priceObj = row.get(vs);
            if (priceObj instanceof Number n) {
                out.put(sym, new BigDecimal(n.toString())); // mejor que double
            }
        }
        return out;
    }
}
