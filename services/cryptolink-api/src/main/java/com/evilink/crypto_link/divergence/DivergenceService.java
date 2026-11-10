package com.evilink.crypto_link.divergence;

import com.evilink.crypto_link.history.HistoricalPricesRepository;
import com.evilink.crypto_link.history.HistoricalPricesRepository.BreadthPoint;
import com.evilink.crypto_link.history.HistoricalPricesRepository.IndexPoint;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DIVERGENCE (precio vs breadth) — hermano de breadth, indicador de estructura.
 * Compara la tendencia del ÍNDICE de precio equal-weight contra la tendencia del
 * BREADTH (% sobre MA). Cuando se contradicen = divergencia (señal adelantada):
 *   - precio ↑ y breadth ↓  = divergencia BAJISTA (rally estrechándose)
 *   - precio ↓ y breadth ↑  = divergencia ALCISTA (venta agotándose)
 *
 * CONSISTENCIA: MA_WINDOW y MIN_POINTS DEBEN coincidir con BreadthService
 * (si cambias allá, cambia aquí) → la línea de breadth es idéntica al panel en vivo.
 * Sin honestidad falsa: NO se inventa un "score" 0-100; se reportan los dos cambios reales.
 */
@Service
public class DivergenceService {

    private final HistoricalPricesRepository repo;

    // === DEBEN espejar BreadthService ===
    private static final int MA_WINDOW = 48;
    private static final int MIN_POINTS = 12;

    // deadbands del readout (evitan flapping por micro-movimientos)
    private static final double IDX_TH = 0.5;  // % del índice
    private static final double BR_TH = 2.0;   // puntos de breadth

    private static final long CACHE_TTL_MS = 55 * 60_000;

    private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();
    private record Cached(DivergenceResult result, long at) {}

    public DivergenceService(HistoricalPricesRepository repo) {
        this.repo = repo;
    }

    public DivergenceResult getDivergence(String fiat, int hours) {
        String key = fiat + ":" + hours;
        long now = System.currentTimeMillis();
        Cached c = cache.get(key);
        if (c != null && (now - c.at()) < CACHE_TTL_MS) return c.result();
        DivergenceResult fresh = compute(fiat, hours);
        cache.put(key, new Cached(fresh, now));
        return fresh;
    }

    private DivergenceResult compute(String fiat, int hours) {
        List<IndexPoint> idx = repo.priceIndexHistory(fiat, hours);
        List<BreadthPoint> br = repo.breadthHistory(fiat, hours, MA_WINDOW, MIN_POINTS);

        List<Point> idxPts = new ArrayList<>(idx.size());
        for (IndexPoint p : idx) idxPts.add(new Point(p.t().toEpochSecond(), round2(p.value())));

        List<Point> brPts = new ArrayList<>(br.size());
        for (BreadthPoint p : br) brPts.add(new Point(p.t().toEpochSecond(), round2(p.pctAbove())));

        // cambio reciente de cada serie (último tercio de la ventana)
        double idxChange = recentChange(idxPts);
        double brChange = recentChange(brPts);
        int recentHours = recentSpanHours(idxPts);

        boolean idxUp = idxChange > IDX_TH, idxDn = idxChange < -IDX_TH;
        boolean brUp = brChange > BR_TH, brDn = brChange < -BR_TH;

        String state;
        if (idxUp && brDn) state = "bearish-divergence";   // precio sube, amplitud baja
        else if (idxDn && brUp) state = "bullish-divergence"; // precio baja, amplitud sube
        else if (idxUp && brUp) state = "aligned-up";
        else if (idxDn && brDn) state = "aligned-down";
        else state = "flat";

        double latestIdx = idxPts.isEmpty() ? 100.0 : idxPts.get(idxPts.size() - 1).v();
        double latestBreadth = brPts.isEmpty() ? 0.0 : brPts.get(brPts.size() - 1).v();

        return new DivergenceResult(
            idxPts, brPts, state,
            round2(idxChange), round2(brChange), recentHours,
            round2(latestIdx), round2(latestBreadth)
        );
    }

    /** Cambio en el último tercio de la serie (último − punto a ~1/3 del final). */
    private double recentChange(List<Point> pts) {
        int n = pts.size();
        if (n < 2) return 0.0;
        int back = Math.max(1, n / 3);
        double from = pts.get(n - 1 - back).v();
        double to = pts.get(n - 1).v();
        return to - from;
    }

    private int recentSpanHours(List<Point> pts) {
        int n = pts.size();
        if (n < 2) return 0;
        int back = Math.max(1, n / 3);
        long dt = pts.get(n - 1).t() - pts.get(n - 1 - back).t();
        return (int) Math.round(dt / 3600.0);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    public record Point(long t, double v) {}
    public record DivergenceResult(
        List<Point> priceIndex,
        List<Point> breadth,
        String state,
        double priceChangePct,     // cambio del índice en el tramo reciente
        double breadthChangePts,   // cambio de breadth (puntos) en el tramo reciente
        int recentHours,
        double latestIndex,
        double latestBreadth
    ) {}
}
