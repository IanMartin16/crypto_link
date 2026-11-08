package com.evilink.crypto_link.compare;

import com.evilink.crypto_link.history.HistoricalPricesRepository;
import com.evilink.crypto_link.history.HistoricalPricesRepository.SeriesPoint;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Series rebaseadas para el chart "performance compare" (estilo TradingView).
 * Lee historical_prices (persistido, hourly) y rebasea cada símbolo a 100 al
 * inicio de la ventana → líneas comparables que cargan LLENAS (no acumulan en
 * cliente). NO toca momentum ni price_history: es aditivo.
 */
@Service
public class CompareService {

    private final HistoricalPricesRepository repo;
    private static final int MAX_POINTS = 120;   // downsample para el chart

    public CompareService(HistoricalPricesRepository repo) {
        this.repo = repo;
    }

    public CompareResult getRebased(List<String> symbols, String fiat, int windowHours) {
        List<Serie> series = new ArrayList<>();

        for (String symbol : symbols) {
            String sym = symbol.trim().toUpperCase();
            List<SeriesPoint> rows = repo.findSeriesForCompare(fiat, sym, windowHours);
            if (rows.size() < 2) continue;   // sin serie suficiente → se omite (no se inventa)

            // base = primer precio válido (> 0) de la ventana
            BigDecimal base = null;
            for (SeriesPoint p : rows) {
                if (p.price() != null && p.price().signum() > 0) { base = p.price(); break; }
            }
            if (base == null) continue;

            List<SeriesPoint> ds = downsample(rows, MAX_POINTS);
            List<Point> pts = new ArrayList<>(ds.size());
            for (SeriesPoint p : ds) {
                if (p.price() == null || p.capturedAt() == null) continue;
                double v = p.price().divide(base, 8, RoundingMode.HALF_UP).doubleValue() * 100.0;
                long t = p.capturedAt().toEpochSecond();   // lightweight-charts: segundos UTC
                pts.add(new Point(t, Math.round(v * 100.0) / 100.0));   // 2 decimales
            }
            if (pts.size() < 2) continue;

            double last = pts.get(pts.size() - 1).v();
            String dir = last > 100.2 ? "up" : last < 99.8 ? "down" : "flat";
            series.add(new Serie(sym, last, dir, pts));
        }

        return new CompareResult(series);
    }

    /** 1 de cada N uniforme, asegurando el punto más reciente. */
    private List<SeriesPoint> downsample(List<SeriesPoint> rows, int target) {
        int n = rows.size();
        if (n <= target) return rows;
        List<SeriesPoint> out = new ArrayList<>(target);
        double step = (double) n / target;
        for (int i = 0; i < target; i++) out.add(rows.get((int) Math.floor(i * step)));
        out.set(out.size() - 1, rows.get(n - 1));
        return out;
    }

    public record Point(long t, double v) {}
    public record Serie(String symbol, double last, String direction, List<Point> points) {}
    public record CompareResult(List<Serie> series) {}
}
