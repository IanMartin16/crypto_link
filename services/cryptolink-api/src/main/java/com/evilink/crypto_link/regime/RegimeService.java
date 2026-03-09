package com.evilink.crypto_link.regime;

import com.evilink.crypto_link.momentum.MomentumService;
import com.evilink.crypto_link.trends.TrendService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class RegimeService {

    private final TrendService trendService;
    private final MomentumService momentumService;

    public RegimeService(TrendService trendService, MomentumService momentumService) {
        this.trendService = trendService;
        this.momentumService = momentumService;
    }

    public RegimeResult getRegime(List<String> symbols, String fiat) {
        List<TrendService.TrendRow> trends = trendService.getTrends(symbols, fiat);
        List<MomentumService.MomentumRow> momentum = momentumService.getMomentum(symbols, fiat);

        double total = 0.0;
        int count = 0;

        for (String symbol : symbols) {
            TrendService.TrendRow tr = trends.stream()
                .filter(x -> x.symbol().equalsIgnoreCase(symbol))
                .findFirst()
                .orElse(null);

            MomentumService.MomentumRow mr = momentum.stream()
                .filter(x -> x.symbol().equalsIgnoreCase(symbol))
                .findFirst()
                .orElse(null);

            double trendScore = directionScore(tr == null ? "flat" : tr.direction());
            double momentumScore = directionScore(mr == null ? "flat" : mr.direction());
            double strengthWeight = strengthWeight(mr == null ? "low" : mr.strength());

            double symbolScore = trendScore + (momentumScore * strengthWeight);

            total += symbolScore;
            count++;
        }

        double avg = count == 0 ? 0.0 : total / count;

        String state;
        if (avg >= 0.75) {
            state = "bullish";
        } else if (avg <= -0.75) {
            state = "bearish";
        } else if (avg > -0.30 && avg < 0.30) {
            state = "neutral";
        } else {
            state = "mixed";
        }

        double confidence = Math.min(1.0, Math.abs(avg) / 1.5);

        String summary = switch (state) {
            case "bullish" -> "Momentum y tendencias favorecen un sesgo alcista moderado.";
            case "bearish" -> "Momentum y tendencias favorecen un sesgo bajista moderado.";
            case "mixed" -> "Las señales muestran un mercado mixto con sesgos encontrados.";
            default -> "Las señales muestran un mercado estable o sin dirección dominante.";
        };

        return new RegimeResult(
            state,
            round(avg),
            round(confidence),
            summary
        );
    }

    private double directionScore(String direction) {
        if (direction == null) return 0.0;
        return switch (direction.toLowerCase()) {
            case "up" -> 1.0;
            case "down" -> -1.0;
            default -> 0.0;
        };
    }

    private double strengthWeight(String strength) {
        if (strength == null) return 0.3;
        return switch (strength.toLowerCase()) {
            case "high" -> 1.0;
            case "medium" -> 0.6;
            default -> 0.3;
        };
    }

    private BigDecimal round(double n) {
        return BigDecimal.valueOf(n).setScale(2, RoundingMode.HALF_UP);
    }

    public record RegimeResult(
        String state,
        BigDecimal score,
        BigDecimal confidence,
        String summary
    ) {}
}