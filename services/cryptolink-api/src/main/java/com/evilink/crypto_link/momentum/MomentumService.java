package com.evilink.crypto_link.momentum;

import com.evilink.crypto_link.history.PriceHistoryCache;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class MomentumService {

    private final PriceHistoryCache historyCache;

    public MomentumService(PriceHistoryCache historyCache) {
        this.historyCache = historyCache;
    }

    public List<MomentumRow> getMomentum(List<String> symbols, String fiat) {
        List<MomentumRow> out = new ArrayList<>();

        for (String symbol : symbols) {
            List<PriceHistoryCache.Point> points = historyCache.get(fiat, symbol);

            if (points == null || points.size() < 3) {
                out.add(new MomentumRow(
                    symbol.toUpperCase(),
                    "flat",
                    BigDecimal.ZERO,
                    "low",
                    BigDecimal.ZERO,
                    null,
                    "insufficient-history"
                ));
                continue;
            }

            BigDecimal first = points.get(0).v;
            BigDecimal last = points.get(points.size() - 1).v;

            if (first == null || last == null || BigDecimal.ZERO.compareTo(first) == 0) {
                out.add(new MomentumRow(
                    symbol.toUpperCase(),
                    "flat",
                    BigDecimal.ZERO,
                    "low",
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

            String direction =
                changePct.compareTo(BigDecimal.ZERO) > 0 ? "up" :
                changePct.compareTo(BigDecimal.ZERO) < 0 ? "down" :
                "flat";

            int favorableSteps = 0;
            int totalSteps = 0;

            for (int i = 1; i < points.size(); i++) {
                BigDecimal prev = points.get(i - 1).v;
                BigDecimal curr = points.get(i).v;
                if (prev == null || curr == null) continue;

                int cmp = curr.compareTo(prev);
                totalSteps++;

                if ("up".equals(direction) && cmp > 0) favorableSteps++;
                if ("down".equals(direction) && cmp < 0) favorableSteps++;
                if ("flat".equals(direction) && cmp == 0) favorableSteps++;
            }

            BigDecimal consistencyRatio = totalSteps == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf((double) favorableSteps / totalSteps);

            BigDecimal score = changePct.abs()
                .multiply(consistencyRatio)
                .setScale(2, RoundingMode.HALF_UP);

            String strength;
            if (score.compareTo(BigDecimal.valueOf(1.00)) >= 0) {
                strength = "high";
            } else if (score.compareTo(BigDecimal.valueOf(0.30)) >= 0) {
                strength = "medium";
            } else {
                strength = "low";
            }

            out.add(new MomentumRow(
                symbol.toUpperCase(),
                direction,
                changePct.setScale(2, RoundingMode.HALF_UP),
                strength,
                score,
                last.setScale(2, RoundingMode.HALF_UP),
                "internal-history"
            ));
        }

        out.sort(Comparator.comparing(MomentumRow::score).reversed());
        return out;
    }

    public record MomentumRow(
        String symbol,
        String direction,
        BigDecimal changePct,
        String strength,
        BigDecimal score,
        BigDecimal last,
        String source
    ) {}
}