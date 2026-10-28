package com.evilink.crypto_link.overview;

import com.evilink.crypto_link.history.HistoricalPricesRepository;
import com.evilink.crypto_link.history.HistoricalPricesRepository.OverviewRow;
import com.evilink.crypto_link.history.HistoricalPricesRepository.PricePoint;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OverviewService {

    private final HistoricalPricesRepository repo;

    private static final int TOP_N = 100;              // compat: getOverview() legacy
    private static final int MAX_RANK = 200;           // TECHO expuesto por paginación (subir a 300 luego)
    private static final int MAX_PAGE_SIZE = 100;
    private static final int WEEK_HOURS = 168;         // 7d
    private static final int SPARK_POINTS = 28;        // ~1 cada 6h en 7d
    private static final int MIN_7D_POINTS = 24;       // al menos ~1 día de serie

    // cache alineado al job (data cambia cada hora), AHORA por fiat
    private final ConcurrentHashMap<String, Snapshot> cacheByFiat = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 55 * 60_000;

    public OverviewService(HistoricalPricesRepository repo) {
        this.repo = repo;
    }

    /** Legacy: top 100 directo (lo que consumía el front antes). Se mantiene por compat. */
    public List<OverviewItem> getOverview(String fiat) {
        return enriched(fiat).items().stream().limit(TOP_N).toList();
    }

    /** Página del overview. 'page' 1-based; 'limit' acotado a [1, MAX_PAGE_SIZE]. */
    public OverviewPage getOverviewPage(String fiat, int page, int limit) {
        Snapshot snap = enriched(fiat);
        List<OverviewItem> all = snap.items();

        int pageSize = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        int total = all.size();
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
        int p = Math.max(1, Math.min(page, totalPages));

        int from = (p - 1) * pageSize;
        int to = Math.min(from + pageSize, total);
        List<OverviewItem> slice = from >= total ? List.of() : all.subList(from, to);

        return new OverviewPage(List.copyOf(slice), p, pageSize, total, totalPages, snap.updatedAt());
    }

    // ---- cache enriquecido (top MAX_RANK con sparkline), una vez por hora por fiat ----
    private Snapshot enriched(String fiat) {
        long now = System.currentTimeMillis();
        Snapshot s = cacheByFiat.get(fiat);
        if (s != null && (now - s.at()) < CACHE_TTL_MS) return s;
        Snapshot fresh = compute(fiat);
        cacheByFiat.put(fiat, fresh);
        return fresh;
    }

    private Snapshot compute(String fiat) {
        List<OverviewRow> latest = repo.findLatestPerSymbol(fiat);

        // ranking por market_cap desc, hasta MAX_RANK (los que tengan mcap)
        List<OverviewRow> top = latest.stream()
            .filter(r -> r.marketCap() != null)
            .sorted(Comparator.comparing(OverviewRow::marketCap).reversed())
            .limit(MAX_RANK)
            .toList();

        List<OverviewItem> out = new ArrayList<>(top.size());
        int rank = 1;
        for (OverviewRow r : top) {
            List<PricePoint> series = repo.findSeriesSince(fiat, r.symbol(), WEEK_HOURS);

            // change_7d: actual vs el más viejo de la ventana. FALLBACK honesto: null -> front "—"
            BigDecimal change7d = null;
            if (series.size() >= MIN_7D_POINTS) {
                BigDecimal first = series.get(0).price();                // más viejo (ASC)
                BigDecimal last = series.get(series.size() - 1).price();  // más reciente
                if (first != null && last != null && first.signum() != 0) {
                    change7d = last.subtract(first)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(first, 2, RoundingMode.HALF_UP);
                }
            }

            List<BigDecimal> spark = downsample(series, SPARK_POINTS);

            out.add(new OverviewItem(
                rank++, r.symbol(), r.price(), r.change24h(), change7d,
                r.volume24h(), r.marketCap(), spark
            ));
        }

        // edad real del dato = snapshot más reciente del universo (para etiqueta honesta)
        OffsetDateTime updatedAt = repo.latestCapturedAt(fiat);
        return new Snapshot(List.copyOf(out), updatedAt, System.currentTimeMillis());
    }

    /** Reduce la serie a ~target puntos tomando 1 de cada N (uniforme). */
    private List<BigDecimal> downsample(List<PricePoint> series, int target) {
        int size = series.size();
        if (size == 0) return List.of();
        if (size <= target) {
            List<BigDecimal> all = new ArrayList<>(size);
            for (PricePoint p : series) all.add(p.price());
            return all;
        }
        List<BigDecimal> out = new ArrayList<>(target);
        double step = (double) size / target;
        for (int i = 0; i < target; i++) {
            out.add(series.get((int) Math.floor(i * step)).price());
        }
        out.set(out.size() - 1, series.get(size - 1).price()); // asegura el punto más reciente
        return out;
    }

    public record OverviewItem(
        int rank, String symbol, BigDecimal price,
        BigDecimal change24h, BigDecimal change7d,   // change7d puede ser null (front "—")
        BigDecimal volume24h, BigDecimal marketCap,
        List<BigDecimal> spark
    ) {}

    public record OverviewPage(
        List<OverviewItem> items, int page, int pageSize,
        int total, int totalPages, OffsetDateTime updatedAt   // updatedAt puede ser null
    ) {}

    private record Snapshot(List<OverviewItem> items, OffsetDateTime updatedAt, long at) {}
}
