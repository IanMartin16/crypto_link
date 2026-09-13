package com.evilink.crypto_link.trends;

import com.evilink.crypto_link.history.PriceHistoryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class TrendService {

    private final PriceHistoryRepository priceHistoryRepo;

    private static final int TREND_POINTS = 24;   // igual al buffer viejo

    public TrendService(PriceHistoryRepository priceHistoryRepo) {
        this.priceHistoryRepo = priceHistoryRepo;
    }

    public List<TrendRow> getTrends(List<String> symbols, String fiat) {
        List<TrendRow> out = new ArrayList<>();

        for (String symbol : symbols) {
            // ANTES: List<PriceHistoryCache.Point> points = historyCache.get(fiat, symbol);
            // AHORA: leer de price_history (persistente). findSeries devuelve DESC → invertir a ASC.
            List<PriceHistoryRepository.PricePointRow> rows =
                priceHistoryRepo.findSeries(fiat, symbol.toUpperCase(), TREND_POINTS);

            List<BigDecimal> prices = new ArrayList<>(rows.size());
            for (int i = rows.size() - 1; i >= 0; i--) {   // DESC → ASC
                prices.add(rows.get(i).price());
            }

            if (prices.size() < 3) {
                BigDecimal lastValue = prices.isEmpty() ? null : prices.get(prices.size() - 1);
                out.add(new TrendRow(
                    symbol.toUpperCase(), "flat", BigDecimal.ZERO, BigDecimal.ZERO,
                    lastValue == null ? null : lastValue.setScale(2, RoundingMode.HALF_UP),
                    "insufficient-history"
                ));
                continue;
            }

            BigDecimal first = prices.get(0);
            BigDecimal last = prices.get(prices.size() - 1);

            if (first == null || last == null || BigDecimal.ZERO.compareTo(first) == 0) {
                out.add(new TrendRow(
                    symbol.toUpperCase(), "flat", BigDecimal.ZERO, BigDecimal.ZERO,
                    last, "invalid-series"
                ));
                continue;
            }

            BigDecimal diff = last.subtract(first);
            BigDecimal changePct = diff
                .multiply(BigDecimal.valueOf(100))
                .divide(first, 4, RoundingMode.HALF_UP);

            String direction;
            int cmpUp = changePct.compareTo(BigDecimal.valueOf(0.20));
            int cmpDown = changePct.compareTo(BigDecimal.valueOf(-0.20));
            if (cmpUp > 0) direction = "up";
            else if (cmpDown < 0) direction = "down";
            else direction = "flat";

            BigDecimal score = changePct.abs().setScale(2, RoundingMode.HALF_UP);

            out.add(new TrendRow(
                symbol.toUpperCase(), direction,
                changePct.setScale(2, RoundingMode.HALF_UP),
                score,
                last.setScale(2, RoundingMode.HALF_UP),
                "price-history-db"     // antes "internal-history"
            ));
        }

        out.sort(Comparator.comparing(TrendRow::score).reversed());
        return out;
    }

    public record TrendRow(
        String symbol, String direction, BigDecimal changePct,
        BigDecimal score, BigDecimal last, String source
    ) {}
}