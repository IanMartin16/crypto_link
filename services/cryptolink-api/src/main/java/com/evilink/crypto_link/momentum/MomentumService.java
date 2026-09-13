package com.evilink.crypto_link.momentum;

import com.evilink.crypto_link.history.PriceHistoryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
public class MomentumService {

    private final PriceHistoryRepository priceHistoryRepo;

    // cuántos puntos leer de la BD para el cálculo de momentum.
    // El buffer daba máx 24; con historia real podemos pedir los últimos N.
    private static final int MOMENTUM_POINTS = 24;

    public MomentumService(PriceHistoryRepository priceHistoryRepo) {
        this.priceHistoryRepo = priceHistoryRepo;
    }

    public List<MomentumRow> getMomentum(List<String> symbols, String fiat) {
        List<MomentumRow> out = new ArrayList<>();

        for (String symbol : symbols) {
            // ANTES: List<PriceHistoryCache.Point> points = historyCache.get(fiat, symbol);
            // AHORA: leer de price_history. findSeries devuelve DESC (reciente primero),
            // el cálculo espera ASC (viejo->nuevo, para que get(0)=first y get(last)=last),
            // así que invertimos.
            List<PriceHistoryRepository.PricePointRow> rows =
                priceHistoryRepo.findSeries(fiat, symbol.toUpperCase(), MOMENTUM_POINTS);

            // extraer solo los precios en orden cronológico ASC
            List<BigDecimal> prices = new ArrayList<>(rows.size());
            for (int i = rows.size() - 1; i >= 0; i--) {   // invertir DESC -> ASC
                prices.add(rows.get(i).price());
            }

            if (prices.size() < 3) {
                BigDecimal lastValue = prices.isEmpty() ? null : prices.get(prices.size() - 1);
                out.add(new MomentumRow(
                    symbol.toUpperCase(), "flat", BigDecimal.ZERO, "low", BigDecimal.ZERO,
                    lastValue == null ? null : lastValue.setScale(2, RoundingMode.HALF_UP),
                    "insufficient-history"
                ));
                continue;
            }

            BigDecimal first = prices.get(0);
            BigDecimal last = prices.get(prices.size() - 1);

            if (first == null || last == null || BigDecimal.ZERO.compareTo(first) == 0) {
                out.add(new MomentumRow(
                    symbol.toUpperCase(), "flat", BigDecimal.ZERO, "low", BigDecimal.ZERO,
                    last, "invalid-series"
                ));
                continue;
            }

            BigDecimal diff = last.subtract(first);
            BigDecimal changePct = diff
                .multiply(BigDecimal.valueOf(100))
                .divide(first, 4, RoundingMode.HALF_UP);

            String direction =
                changePct.compareTo(BigDecimal.ZERO) > 0 ? "up" :
                changePct.compareTo(BigDecimal.ZERO) < 0 ? "down" : "flat";

            int favorableSteps = 0;
            int totalSteps = 0;
            for (int i = 1; i < prices.size(); i++) {
                BigDecimal prev = prices.get(i - 1);
                BigDecimal curr = prices.get(i);
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
            if (score.compareTo(BigDecimal.valueOf(1.00)) >= 0) strength = "high";
            else if (score.compareTo(BigDecimal.valueOf(0.30)) >= 0) strength = "medium";
            else strength = "low";

            out.add(new MomentumRow(
                symbol.toUpperCase(), direction,
                changePct.setScale(2, RoundingMode.HALF_UP),
                strength, score,
                last.setScale(2, RoundingMode.HALF_UP),
                "price-history-db"     // antes "internal-history"; marca que viene de la BD
            ));
        }

        out.sort(Comparator.comparing(MomentumRow::score).reversed());
        return out;
    }

    public record MomentumRow(
        String symbol, String direction, BigDecimal changePct,
        String strength, BigDecimal score, BigDecimal last, String source
    ) {}
}