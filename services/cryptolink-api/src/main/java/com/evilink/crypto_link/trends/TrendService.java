package com.evilink.crypto_link.trends;

import com.evilink.crypto_link.history.PriceHistoryCache;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class TrendService {

    private final PriceHistoryCache historyCache;

    public TrendService(PriceHistoryCache historyCache) {
        this.historyCache = historyCache;
    }

    public List<TrendRow> getTrends(List<String> symbols, String fiat) {
        List<TrendRow> out = new ArrayList<>();

        for (String symbol : symbols) {
            List<PriceHistoryCache.Point> points = historyCache.get(fiat, symbol);
            if (points == null || points.size() < 3) {
               BigDecimal lastValue = null;
               if (points != null && !points.isEmpty()) {
                  lastValue = points.get(points.size() - 1).v;
            }

            out.add(new TrendRow(
               symbol.toUpperCase(),
               "flat",
               BigDecimal.ZERO,
               BigDecimal.ZERO,
               lastValue == null ? null : lastValue.setScale(2, RoundingMode.HALF_UP),
               "insufficient-history"
            ));
            continue;
        }

            BigDecimal first = points.get(0).v;
            BigDecimal last = points.get(points.size() - 1).v;

            if (first == null || last == null || BigDecimal.ZERO.compareTo(first) == 0) {
                out.add(new TrendRow(
                    symbol.toUpperCase(),
                    "flat",
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    last,
                    "invalid-series"
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

            if (cmpUp > 0) {
                direction = "up";
            } else if (cmpDown < 0) {
                direction = "down";
            } else {
                direction = "flat";
            }

            BigDecimal score = changePct.abs().setScale(2, RoundingMode.HALF_UP);

            out.add(new TrendRow(
                symbol.toUpperCase(),
                direction,
                changePct.setScale(2, RoundingMode.HALF_UP),
                score,
                last.setScale(2, RoundingMode.HALF_UP),
                "internal-history"
            ));
        }

        out.sort(Comparator.comparing(TrendRow::score).reversed());
        return out;
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