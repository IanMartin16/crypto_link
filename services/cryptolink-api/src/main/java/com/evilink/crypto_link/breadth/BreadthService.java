package com.evilink.crypto_link.breadth;

import com.evilink.crypto_link.history.HistoricalPricesRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class BreadthService {

    private final HistoricalPricesRepository repo;

    // ventana de la MA en PUNTOS (el job es 1h → 48 puntos ≈ 2 días).
    // Se puede subir conforme historical_prices acumule (168=7d, 720=30d).
    private static final int MA_WINDOW = 48;
    // mínimo de puntos para considerar un símbolo "con serie válida".
    // Menos que esto = estable/sin-datos (USYC) → se excluye, no penaliza.
    private static final int MIN_POINTS = 12;

    public BreadthService(HistoricalPricesRepository repo) {
        this.repo = repo;
    }

    public BreadthResult getBreadth(String fiat) {
        List<String> symbols = repo.findSymbols(fiat);

        int above = 0;     // precio actual > MA
        int below = 0;     // precio actual < MA
        int neutral = 0;   // exactamente igual (raro)
        int excluded = 0;  // sin serie suficiente (estables/nuevos) → no cuentan

        for (String sym : symbols) {
            // traemos hasta MA_WINDOW puntos (DESC: el primero es el actual)
            List<BigDecimal> series = repo.findPriceSeries(fiat, sym, MA_WINDOW);

            if (series.size() < MIN_POINTS) {
                excluded++;   // estable/sin-datos como USYC → fuera del cálculo
                continue;
            }

            BigDecimal current = series.get(0);  // el más reciente (DESC)

            // MA simple sobre los puntos disponibles
            BigDecimal sum = BigDecimal.ZERO;
            for (BigDecimal p : series) sum = sum.add(p);
            BigDecimal ma = sum.divide(BigDecimal.valueOf(series.size()), 8, RoundingMode.HALF_UP);

            int cmp = current.compareTo(ma);
            if (cmp > 0) above++;
            else if (cmp < 0) below++;
            else neutral++;
        }

        int movers = above + below;   // los que SÍ se mueven (excluye neutrales/estables)
            BigDecimal pctAbove = movers == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(above)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(movers), 1, RoundingMode.HALF_UP);

        // lectura cualitativa honesta
        String reading;
        double pct = pctAbove.doubleValue();
        if (movers < 5) reading = "insufficient-coverage";
        else if (pct >= 70) reading = "broad-strength";      // amplitud sana
        else if (pct >= 55) reading = "leaning-positive";
        else if (pct > 45)  reading = "mixed";
        else if (pct > 30)  reading = "leaning-negative";
        else                reading = "broad-weakness";      // frágil/pocos suben

        return new BreadthResult(
            pctAbove, above, below, neutral, excluded, movers, MA_WINDOW, reading
        );
    }

    public record BreadthResult(
        BigDecimal pctAbove,   // % de los evaluados por encima de su MA
        int above,
        int below,
        int neutral,
        int excluded,          // estables/sin-serie (transparencia: cuántos no contaron)
        int movers,         // universo real del cálculo
        int maWindowPoints,    // ventana usada (para que el front muestre "MA ~2d")
        String reading         // lectura cualitativa
    ) {}
}