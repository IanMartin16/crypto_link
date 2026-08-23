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

        // contadores de participación (datos reales que hoy se tiran del texto)
        int upCount = 0;
        int downCount = 0;

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

            if (symbolScore > 0.15) upCount++;
            else if (symbolScore < -0.15) downCount++;

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

        String summary = buildSummary(state, avg, confidence, upCount, downCount, count);

        return new RegimeResult(
            state,
            round(avg),
            round(confidence),
            summary
        );
    }

    /**
     * Summary compuesto de datos reales: fuerza del sesgo (avg), confianza, y
     * participación (cuántos suben vs bajan). Antes: una frase fija por estado.
     */
    private String buildSummary(String state, double avg, double confidence,
                                int up, int down, int total) {
        // fuerza del sesgo según magnitud del promedio
        double mag = Math.abs(avg);
        String strength;
        if (mag >= 0.9) strength = "strong";
        else if (mag >= 0.5) strength = "moderate";
        else strength = "mild";

        // confianza en lenguaje llano
        String conf = confidence >= 0.66 ? "high" : confidence >= 0.33 ? "moderate" : "low";

        // participación: cuántos del grupo acompañan la dirección
        String participation;
        if (total > 0) {
            if (state.equals("bullish")) {
                participation = up + " of " + total + " leaning up";
            } else if (state.equals("bearish")) {
                participation = down + " of " + total + " leaning down";
            } else {
                participation = up + " up / " + down + " down of " + total;
            }
        } else {
            participation = "no coverage";
        }

        return switch (state) {
            case "bullish" -> "A " + strength + " bullish lean with " + conf
                + " confidence — " + participation + ".";
            case "bearish" -> "A " + strength + " bearish lean with " + conf
                + " confidence — " + participation + ".";
            case "mixed" -> "Direction is split (" + participation
                + "), so no clean bias — " + conf + " confidence.";
            default -> "No dominant direction right now (" + participation
                + ") — the group is holding roughly flat.";
        };
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