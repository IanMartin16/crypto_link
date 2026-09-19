package com.evilink.crypto_link.overview;

import com.evilink.crypto_link.history.HistoricalPricesRepository;
import com.evilink.crypto_link.history.HistoricalPricesRepository.OverviewRow;
import com.evilink.crypto_link.history.HistoricalPricesRepository.PricePoint;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class OverviewService {

    private final HistoricalPricesRepository repo;

    private static final int TOP_N = 100;
    private static final int WEEK_HOURS = 168;         // 7d
    private static final int SPARK_POINTS = 28;        // ~1 cada 6h en 7d
    // mínimo de puntos en la ventana 7d para calcular change_7d honesto
    private static final int MIN_7D_POINTS = 24;       // al menos ~1 día de serie

    // cache alineado al job (data cambia cada hora)
    private volatile List<OverviewItem> cached;
    private volatile long cachedAt = 0;
    private static final long CACHE_TTL_MS = 55 * 60_000;

    public OverviewService(HistoricalPricesRepository repo) {
        this.repo = repo;
    }

    public List<OverviewItem> getOverview(String fiat) {
        long now = System.currentTimeMillis();
        List<OverviewItem> c = cached;
        if (c != null && (now - cachedAt) < CACHE_TTL_MS) return c;
        List<OverviewItem> fresh = compute(fiat);
        cached = fresh;
        cachedAt = now;
        return fresh;
    }

    private List<OverviewItem> compute(String fiat) {
        List<OverviewRow> latest = repo.findLatestPerSymbol(fiat);

        // ranking por market_cap desc, top 100 (los que tengan mcap)
        List<OverviewRow> top = latest.stream()
            .filter(r -> r.marketCap() != null)
            .sorted(Comparator.comparing(OverviewRow::marketCap).reversed())
            .limit(TOP_N)
            .toList();

        List<OverviewItem> out = new ArrayList<>(top.size());
        int rank = 1;
        for (OverviewRow r : top) {
            List<PricePoint> series = repo.findSeriesSince(fiat, r.symbol(), WEEK_HOURS);

            // change_7d: precio actual vs el más viejo de la ventana 7d.
            // FALLBACK honesto: si no hay serie suficiente → null (front muestra "—").
            BigDecimal change7d = null;
            if (series.size() >= MIN_7D_POINTS) {
                BigDecimal first = series.get(0).price();               // más viejo (ASC)
                BigDecimal last = series.get(series.size() - 1).price(); // más reciente
                if (first != null && last != null && first.signum() != 0) {
                    change7d = last.subtract(first)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(first, 2, RoundingMode.HALF_UP);
                }
            }

            // sparkline downsampled a ~SPARK_POINTS
            List<BigDecimal> spark = downsample(series, SPARK_POINTS);

            out.add(new OverviewItem(
                rank++, r.symbol(), r.price(), r.change24h(), change7d,
                r.volume24h(), r.marketCap(), spark
            ));
        }
        return out;
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
        // asegurar que el último punto real esté (el precio más reciente)
        out.set(out.size() - 1, series.get(size - 1).price());
        return out;
    }

    public record OverviewItem(
        int rank, String symbol, BigDecimal price,
        BigDecimal change24h, BigDecimal change7d,  // change7d puede ser null (front "—")
        BigDecimal volume24h, BigDecimal marketCap,
        List<BigDecimal> spark
    ) {}
}