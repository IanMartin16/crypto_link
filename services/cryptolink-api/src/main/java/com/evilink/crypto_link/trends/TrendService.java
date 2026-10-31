
package com.evilink.crypto_link.trends;

import com.evilink.crypto_link.history.PriceHistoryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class TrendService {

    private final PriceHistoryRepository priceHistoryRepo;

    private static final int TREND_POINTS = 24;

    private static final BigDecimal DIRECTION_THRESHOLD =
        new BigDecimal("0.20");

    public TrendService(PriceHistoryRepository priceHistoryRepo) {
        this.priceHistoryRepo = priceHistoryRepo;
    }

    public List<TrendRow> getTrends(List<String> symbols, String fiat) {

        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }

        List<TrendRow> out = new ArrayList<>(symbols.size());

        for (String requestedSymbol : symbols) {

            String symbol = requestedSymbol.toUpperCase(Locale.ROOT);

            // El repositorio devuelve DESC:
            // rows[0] = observación más reciente.
            var rows = priceHistoryRepo.findSeries(
                fiat, symbol, TREND_POINTS
            );

            BigDecimal latestPrice = rows.isEmpty()
                ? null
                : rows.get(0) == null
                    ? null
                    : rows.get(0).price();

            // Validar integridad antes de calcular.
            boolean invalid = rows.stream().anyMatch(
                row -> row == null
                    || !validPrice(row.price())
                    || row.capturedAt() == null
            );

            if (invalid) {
                out.add(new TrendRow(
                    symbol,
                    "flat",
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    validPrice(latestPrice) ? latestPrice : null,
                    "invalid-series"
                ));
                continue;
            }

            if (rows.size() < 3) {
                out.add(new TrendRow(
                    symbol,
                    "flat",
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    latestPrice,
                    "insufficient-history"
                ));
                continue;
            }

            // No necesitamos invertir la lista:
            // la fórmula utiliza los extremos cronológicos.
            BigDecimal first = rows.get(rows.size() - 1).price();
            BigDecimal last = rows.get(0).price();

            BigDecimal changePct = last.subtract(first)
                .multiply(BigDecimal.valueOf(100))
                .divide(first, 4, RoundingMode.HALF_UP);

            String direction;

            if (changePct.compareTo(DIRECTION_THRESHOLD) > 0) {
                direction = "up";
            } else if (changePct.compareTo(
                DIRECTION_THRESHOLD.negate()) < 0) {
                direction = "down";
            } else {
                direction = "flat";
            }

            BigDecimal score = changePct.abs()
                .setScale(2, RoundingMode.HALF_UP);

            out.add(new TrendRow(
                symbol,
                direction,
                changePct.setScale(2, RoundingMode.HALF_UP),
                score,
                last, // Precisión original de price_history
                "price-history-db"
            ));
        }

        out.sort(
            Comparator.comparing(TrendRow::score).reversed()
        );

        return out;
    }

    private boolean validPrice(BigDecimal price) {
        return price != null && price.signum() > 0;
    }

    public record TrendRow(
        String symbol,
        String direction,
        BigDecimal changePct,
        BigDecimal score,
        BigDecimal last,
        String source
    ) {}
}
