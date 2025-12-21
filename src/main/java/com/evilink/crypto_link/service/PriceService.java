package com.evilink.crypto_link.service;

import com.evilink.crypto_link.metrics.ApiMetrics;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PriceService {

    private final CoinGeckoPriceProvider provider;
    private final PriceCache cache;
    private final ApiMetrics metrics;
    private static final Logger log = LoggerFactory.getLogger(PriceService.class);

    // TTL corto para MVP (evita pegarle demasiado a CoinGecko)
    private final long ttlMs = 3000; // 3 segundos

    public PriceService(CoinGeckoPriceProvider provider, PriceCache cache, ApiMetrics metrics) {
        this.provider = provider;
        this.cache = cache;
        this.metrics = metrics;
    }

    public Result getPrices(List<String> symbols, String fiat) {
        String symbolsCsv = symbols.stream()
                .map(s -> s.trim().toUpperCase())
                .filter(s -> !s.isBlank())
                .distinct()
                .collect(Collectors.joining(","));

        String key = PriceCache.key(fiat, symbolsCsv);

        long now = System.currentTimeMillis();
        PriceCache.Entry entry = cache.get(key);

        // 1) si hay cache fresco, regresa cache
        if (entry != null && entry.isFresh(now)) {
            return Result.from(entry.prices, fiat, "cache", entry.fetchedAtEpochMs);
        }

        // 2) si no, intenta proveedor
        try {
            Map<String, BigDecimal> fresh = provider.getPrices(Arrays.asList(symbolsCsv.split(",")), fiat);
            cache.put(key, fresh, ttlMs);
            return Result.from(fresh, fiat, "coingecko", System.currentTimeMillis());
        } catch (Exception e) {
            metrics.incUpstreamError("coingecko");
            log.warn("Upstream error provider=coingecko fiat={} symbols={}", fiat, symbolsCsv, e);
            // 3) si falla proveedor y hay cache viejo, regresa stale
            if (entry != null) {
                return Result.from(entry.prices, fiat, "stale-cache", entry.fetchedAtEpochMs);
            }
            // 4) si no hay nada, truena (lo convertimos a 502 en controller)
            throw e;
        }
    }

    public static class Result {
        public final Map<String, BigDecimal> prices;
        public final String fiat;
        public final String source;
        public final String ts;

        private Result(Map<String, BigDecimal> prices, String fiat, String source, String ts) {
            this.prices = prices;
            this.fiat = fiat;
            this.source = source;
            this.ts = ts;
        }

        static Result from(Map<String, BigDecimal> prices, String fiat, String source, long fetchedAtMs) {
            String ts = OffsetDateTime.now().toString();
            return new Result(prices, fiat.toUpperCase(), source, ts);
        }
    }
}
