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
                "No hay suficiente histórico para evaluar anomalías."
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

                String severity;
                double signalScore = Math.max(
                    avgAbsChange > 0 ? absChange / avgAbsChange : 1.0,
                    avgScore > 0 ? score / avgScore : 1.0
                );

                if (signalScore >= 2.5) {
                    severity = "high";
                } else if (signalScore >= 1.8) {
                    severity = "medium";
                } else {
                    severity = "low";
                }

                String detail = unusualMomentum
                    ? m.symbol() + " muestra un momentum significativamente superior al resto del grupo."
                    : m.symbol() + " muestra una variación significativamente superior al resto del grupo.";

                anomalies.add(new AnomalyRow(
                    m.symbol(),
                    type,
                    severity,
                    round(signalScore),
                    detail
                ));
            }
        }

        String summary = anomalies.isEmpty()
            ? "No se detectan anomalías relevantes por ahora."
            : "Se detectaron movimientos o señales fuera del patrón reciente.";

        return new AnomalyResult(anomalies, summary);
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