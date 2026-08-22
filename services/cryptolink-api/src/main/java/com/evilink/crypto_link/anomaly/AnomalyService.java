package com.evilink.crypto_link.anomaly;

import com.evilink.crypto_link.momentum.MomentumService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class AnomalyService {

    private final MomentumService momentumService;

    public AnomalyService(MomentumService momentumService) {
        this.momentumService = momentumService;
    }

    public AnomalyResult getAnomalies(List<String> symbols, String fiat) {
        List<MomentumService.MomentumRow> momentum = momentumService.getMomentum(symbols, fiat);

        List<MomentumService.MomentumRow> usable = momentum.stream()
            .filter(m -> !"insufficient-history".equalsIgnoreCase(m.source()))
            .toList();

        if (usable.isEmpty()) {
            return new AnomalyResult(
                List.of(),
                "Not enough recent history yet to judge what counts as unusual."
            );
        }

        double avgAbsChange = usable.stream()
            .mapToDouble(m -> Math.abs(m.changePct().doubleValue()))
            .average()
            .orElse(0.0);

        double avgScore = usable.stream()
            .mapToDouble(m -> m.score().doubleValue())
            .average()
            .orElse(0.0);

        List<AnomalyRow> anomalies = new ArrayList<>();

        for (MomentumService.MomentumRow m : usable) {
            double absChange = Math.abs(m.changePct().doubleValue());
            double score = m.score().doubleValue();

            boolean unusualMove = avgAbsChange > 0 && absChange > avgAbsChange * 1.8;
            boolean unusualMomentum = avgScore > 0 && score > avgScore * 1.8;

            if (unusualMove || unusualMomentum) {
                String type = unusualMomentum ? "momentum_spike" : "unusual_move";

                double signalScore = Math.max(
                    avgAbsChange > 0 ? absChange / avgAbsChange : 1.0,
                    avgScore > 0 ? score / avgScore : 1.0
                );

                String severity;
                if (signalScore >= 2.5) {
                    severity = "high";
                } else if (signalScore >= 1.8) {
                    severity = "medium";
                } else {
                    severity = "low";
                }

                // TEXTO RICO: compuesto de datos REALES (magnitud, severidad, dirección),
                // no una frase fija. Varía porque los números varían, sin inventar nada.
                String detail = buildDetail(
                    m.symbol(),
                    unusualMomentum,
                    severity,
                    signalScore,
                    m.changePct().doubleValue()
                );

                anomalies.add(new AnomalyRow(
                    m.symbol(),
                    type,
                    severity,
                    round(signalScore),
                    detail
                ));
            }
        }

        String summary = buildSummary(anomalies);

        return new AnomalyResult(anomalies, summary);
    }

    /**
     * Detail construido de datos reales: cuántas veces sobre el promedio del grupo
     * (signalScore), si es momentum o variación, la dirección, y la severidad.
     * Dos anomalías distintas leen distinto porque SUS NÚMEROS son distintos.
     */
    private String buildDetail(
        String symbol,
        boolean isMomentum,
        String severity,
        double signalScore,
        double changePct
    ) {
        // "2.4x" — cuántas veces por encima del promedio del grupo
        String multiple = round(signalScore).stripTrailingZeros().toPlainString() + "x";

        String dimension = isMomentum ? "momentum" : "price variation";

        // dirección solo aplica de forma clara al movimiento de precio
        String direction;
        if (!isMomentum) {
            direction = changePct > 0 ? " to the upside" : changePct < 0 ? " to the downside" : "";
        } else {
            direction = "";
        }

        // frase base según severidad — el adverbio refleja el dato, no adorno al azar
        String intensity = switch (severity) {
            case "high" -> "sharply";
            case "medium" -> "clearly";
            default -> "modestly";
        };

        // ej: "APT is running clearly ahead of the group on momentum, about 2.1x the group average."
        // ej: "SOL is moving sharply ahead of the group on price variation to the upside, about 2.7x the group average."
        return symbol
            + " is running " + intensity
            + " ahead of the group on " + dimension + direction
            + ", about " + multiple + " the group average.";
    }

    /**
     * Summary que refleja cuántas y de qué severidad, en vez de una frase fija.
     */
    private String buildSummary(List<AnomalyRow> anomalies) {
        if (anomalies.isEmpty()) {
            return "Nothing is moving far enough from the group to stand out right now.";
        }

        long high = anomalies.stream().filter(a -> "high".equals(a.severity())).count();
        int total = anomalies.size();

        String noun = total == 1 ? "signal" : "signals";

        if (high > 0) {
            String highNoun = high == 1 ? "one standing out strongly" : high + " standing out strongly";
            return total + " " + noun + " outside the recent pattern, with " + highNoun + ".";
        }

        return total + " " + noun + " drifting outside the recent pattern, none extreme.";
    }

    private BigDecimal round(double n) {
        return BigDecimal.valueOf(n).setScale(2, RoundingMode.HALF_UP);
    }

    public record AnomalyRow(
        String symbol,
        String type,
        String severity,
        BigDecimal score,
        String detail
    ) {}

    public record AnomalyResult(
        List<AnomalyRow> anomalies,
        String summary
    ) {}
}