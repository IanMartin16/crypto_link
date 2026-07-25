package com.evilink.crypto_link.service;

import com.evilink.crypto_link.metrics.ApiMetrics;
import com.evilink.crypto_link.history.PriceHistoryCache;
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
    private final PriceHistoryCache historyCache;
    private static final Logger log = LoggerFactory.getLogger(PriceService.class);

    // HARDENING: la fuente (CoinGecko keyless) actualiza ~cada 60s. Pedir cada 3s
    // traía el MISMO dato 20 veces/min -> 429, stale-cache, latencia, y puntos
    // duplicados en el historial (derivados planos). TTL alineado por debajo de
    // 60s (con margen) para capturar cada update sin sobre-pedir.
    private final long ttlMs = 50_000; // 50 segundos (antes 3s)

    public PriceService(CoinGeckoPriceProvider provider, PriceCache cache, ApiMetrics metrics, PriceHistoryCache historyCache) {
        this.provider = provider;
        this.cache = cache;
        this.metrics = metrics;
        this.historyCache = historyCache;
    }

    public Result getPrices(List<String> symbols, String fiat) {
        String symbolsCsv = symbols.stream()
                .map(s -> s.trim().toUpperCase())
                .filter(s -> !s.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.joining(","));

        String key = PriceCache.key(fiat, symbolsCsv);

        long now = System.currentTimeMillis();
        PriceCache.Entry entry = cache.get(key);

        // 1) cache fresco -> NO se registra en historyCache (evita puntos duplicados:
        //    el precio no cambió desde que se guardó). Solo sirve el dato.
        if (entry != null && entry.isFresh(now)) {
            return Result.from(entry.prices, fiat, "cache", entry.fetchedAtEpochMs);
        }

        // 2) proveedor -> dato NUEVO real -> AQUÍ sí se registra en el historial.
        //    Un punto por cada cambio real (~cada 50-60s) = derivados limpios.
        try {
            Map<String, BigDecimal> fresh = provider.getPrices(Arrays.asList(symbolsCsv.split(",")), fiat);
            cache.put(key, fresh, ttlMs);
            fresh.forEach((symbol, value) -> historyCache.add(fiat, symbol, value));
            return Result.from(fresh, fiat, "coingecko", System.currentTimeMillis());
        } catch (Exception e) {
            metrics.incUpstreamError("coingecko");
            log.warn("Upstream error provider=coingecko fiat={} symbols={}", fiat, symbolsCsv, e);
            // 3) proveedor falla + hay cache viejo -> stale. Tampoco registra en
            //    historial (no es dato nuevo, evita ensuciar la serie).
            if (entry != null) {
                return Result.from(entry.prices, fiat, "stale-cache", entry.fetchedAtEpochMs);
            }
            // 4) nada que servir -> truena (502 en controller)
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
