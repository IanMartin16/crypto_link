package com.evilink.crypto_link.service;

import com.evilink.crypto_link.metrics.ApiMetrics;
import com.evilink.crypto_link.history.PriceHistoryCache;
import com.evilink.crypto_link.history.PriceHistoryRepository;
import com.evilink.crypto_link.service.CoinGeckoPriceProvider.PricePoint;
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
    private final PriceHistoryRepository priceHistoryRepository;
    private static final Logger log = LoggerFactory.getLogger(PriceService.class);

    // HARDENING previo (se mantiene): fuente actualiza ~60s -> TTL 50s.
    private final long ttlMs = 50_000;

    public PriceService(CoinGeckoPriceProvider provider, PriceCache cache, ApiMetrics metrics, PriceHistoryCache historyCache, PriceHistoryRepository priceHistoryRepository) {
        this.provider = provider;
        this.cache = cache;
        this.metrics = metrics;
        this.historyCache = historyCache;
        this.priceHistoryRepository = priceHistoryRepository;
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

        // 1) cache fresco -> sirve, NO registra en historial (evita duplicados)
        if (entry != null && entry.isFresh(now)) {
            return Result.from(entry.points, fiat, "cache", entry.fetchedAtEpochMs);
        }

        // 2) proveedor -> dato NUEVO real (precio + 24h + marketCap en 1 llamada)
        try {
            Map<String, PricePoint> fresh = provider.getPricesRich(Arrays.asList(symbolsCsv.split(",")), fiat);
            cache.put(key, fresh, ttlMs);

            // HARDENING intacto: el buffer en memoria recibe SOLO el precio (los derivados
            // usan precio). La BD recibe el dato rico (price + 24h + marketCap).
            OffsetDateTime now2 = OffsetDateTime.now();
            fresh.forEach((symbol, p) -> {
                historyCache.add(fiat, symbol, p.price);
                priceHistoryRepository.save(
                    fiat.toUpperCase(), symbol.toUpperCase(),
                    p.price, p.change24h, p.marketCap, now2
                );
            });

            return Result.from(fresh, fiat, "coingecko", System.currentTimeMillis());
        } catch (Exception e) {
            metrics.incUpstreamError("coingecko");
            log.warn("Upstream error provider=coingecko fiat={} symbols={}", fiat, symbolsCsv, e);
            // 3) proveedor falla + cache viejo -> stale, sin registrar historial
            if (entry != null) {
                return Result.from(entry.points, fiat, "stale-cache", entry.fetchedAtEpochMs);
            }
            throw e;
        }
    }

    public static class Result {
        public final Map<String, BigDecimal> prices;      // COMPAT: solo precio
        public final Map<String, BigDecimal> change24h;   // NUEVO
        public final Map<String, BigDecimal> marketCap;   // NUEVO
        public final String fiat;
        public final String source;
        public final String ts;

        private Result(Map<String, BigDecimal> prices,
                       Map<String, BigDecimal> change24h,
                       Map<String, BigDecimal> marketCap,
                       String fiat, String source, String ts) {
            this.prices = prices;
            this.change24h = change24h;
            this.marketCap = marketCap;
            this.fiat = fiat;
            this.source = source;
            this.ts = ts;
        }

        static Result from(Map<String, PricePoint> points, String fiat, String source, long fetchedAtMs) {
            Map<String, BigDecimal> prices = new LinkedHashMap<>();
            Map<String, BigDecimal> change24h = new LinkedHashMap<>();
            Map<String, BigDecimal> marketCap = new LinkedHashMap<>();
            points.forEach((sym, p) -> {
                prices.put(sym, p.price);
                if (p.change24h != null) change24h.put(sym, p.change24h);
                if (p.marketCap != null) marketCap.put(sym, p.marketCap);
            });
            String ts = OffsetDateTime.now().toString();
            return new Result(prices, change24h, marketCap, fiat.toUpperCase(), source, ts);
        }
    }
}
