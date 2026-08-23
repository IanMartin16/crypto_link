package com.evilink.crypto_link.health;

import com.evilink.crypto_link.anomaly.AnomalyService;
import com.evilink.crypto_link.momentum.MomentumService;
import com.evilink.crypto_link.regime.RegimeService;
import com.evilink.crypto_link.risk.RiskFlagService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MarketHealthService {

    private final RegimeService regimeService;
    private final MomentumService momentumService;
    private final RiskFlagService riskFlagService;
    private final AnomalyService anomalyService;

    public MarketHealthService(
        RegimeService regimeService,
        MomentumService momentumService,
        RiskFlagService riskFlagService,
        AnomalyService anomalyService
    ) {
        this.regimeService = regimeService;
        this.momentumService = momentumService;
        this.riskFlagService = riskFlagService;
        this.anomalyService = anomalyService;
    }

    public MarketHealthResult getMarketHealth(List<String> symbols, String fiat) {
        var regime = regimeService.getRegime(symbols, fiat);
        var momentum = momentumService.getMomentum(symbols, fiat);
        var risk = riskFlagService.getRiskFlags(symbols, fiat);
        var anomalies = anomalyService.getAnomalies(symbols, fiat);

        int score = 100;

        // 1) Regime direction + confidence
        String regimeState = regime.state();
        double confidence = regime.confidence().doubleValue();

        if ("bearish".equalsIgnoreCase(regimeState)) {
            score -= 28;
        } else if ("mixed".equalsIgnoreCase(regimeState)) {
            score -= 12;
        } else if ("neutral".equalsIgnoreCase(regimeState)) {
            score -= 5;
        } else if ("bullish".equalsIgnoreCase(regimeState)) {
            score += 4;
        }

        if (confidence < 0.10) {
            score -= 25;
        } else if (confidence < 0.25) {
            score -= 15;
        } else if (confidence < 0.50) {
            score -= 8;
        }

        // 2) Momentum quality
        long usefulMomentum = momentum.stream()
            .filter(m -> !"insufficient-history".equalsIgnoreCase(m.source()))
            .count();

        long lowMomentum = momentum.stream()
            .filter(m -> !"insufficient-history".equalsIgnoreCase(m.source()))
            .filter(m -> "low".equalsIgnoreCase(m.strength()))
            .count();

        if (usefulMomentum == 0) {
            score -= 20;
        } else if (lowMomentum == usefulMomentum) {
            score -= 15;
        }

        // 3) Risk flags
        long highRisk = risk.flags().stream()
            .filter(f -> "high".equalsIgnoreCase(f.severity()))
            .count();

        long mediumRisk = risk.flags().stream()
            .filter(f -> "medium".equalsIgnoreCase(f.severity()))
            .count();

        long lowRisk = risk.flags().stream()
            .filter(f -> "low".equalsIgnoreCase(f.severity()))
            .count();

        score -= (int) (highRisk * 18);
        score -= (int) (mediumRisk * 10);
        score -= (int) (lowRisk * 4);

        // 4) Anomalies
        long anomalyCount = anomalies.anomalies().size();
        score -= (int) (anomalyCount * 10);

        score = Math.max(0, Math.min(100, score));

        String state;
        if (score >= 75) {
            state = "healthy";
        } else if (score >= 55) {
            state = "stable";
        } else if (score >= 35) {
            state = "fragile";
        } else {
            state = "under_pressure";
        }

        // TEXTO RICO: nombra los factores REALES que movieron el score, en vez de
        // una frase fija por estado. El usuario ve POR QUÉ, no solo el veredicto.
        String summary = buildSummary(
            state, regimeState, confidence,
            usefulMomentum, lowMomentum,
            highRisk, mediumRisk, lowRisk,
            anomalyCount
        );

        return new MarketHealthResult(state, score, summary);
    }

    /**
     * Summary que nombra los drivers reales del score. Recolecta los factores que
     * de verdad pesaron y arma una frase con ellos, en vez de un texto fijo.
     */
    private String buildSummary(
        String state, String regimeState, double confidence,
        long usefulMomentum, long lowMomentum,
        long highRisk, long mediumRisk, long lowRisk,
        long anomalyCount
    ) {
        // encabezado según estado
        String head = switch (state) {
            case "healthy" -> "Conditions look healthy";
            case "stable" -> "Conditions are stable";
            case "fragile" -> "Conditions look fragile";
            default -> "Conditions are under pressure";
        };

        // recolectar los factores que realmente pesaron (datos reales)
        List<String> drivers = new ArrayList<>();

        if ("bearish".equalsIgnoreCase(regimeState)) {
            drivers.add("a bearish regime");
        } else if ("mixed".equalsIgnoreCase(regimeState)) {
            drivers.add("a mixed regime");
        } else if ("bullish".equalsIgnoreCase(regimeState)) {
            drivers.add("a bullish regime");
        }

        if (confidence < 0.25) {
            drivers.add("low read confidence");
        }

        if (usefulMomentum == 0) {
            drivers.add("no usable momentum history");
        } else if (lowMomentum == usefulMomentum) {
            drivers.add("weak momentum across the board");
        }

        long totalRisk = highRisk + mediumRisk + lowRisk;
        if (highRisk > 0) {
            drivers.add(highRisk + " high-severity risk " + (highRisk == 1 ? "flag" : "flags"));
        } else if (totalRisk > 0) {
            drivers.add(totalRisk + " risk " + (totalRisk == 1 ? "flag" : "flags"));
        }

        if (anomalyCount > 0) {
            drivers.add(anomalyCount + " " + (anomalyCount == 1 ? "anomaly" : "anomalies"));
        }

        if (drivers.isEmpty()) {
            // healthy sin drivers negativos: lo positivo también es un dato real
            return head + " — no risk flags or anomalies, and the regime is holding.";
        }

        // unir los drivers de forma legible: "a, b and c"
        String joined = joinReadable(drivers);
        return head + " — driven by " + joined + ".";
    }

    /** Une una lista como "a", "a and b", "a, b and c". */
    private String joinReadable(List<String> parts) {
        int n = parts.size();
        if (n == 1) return parts.get(0);
        if (n == 2) return parts.get(0) + " and " + parts.get(1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n - 1; i++) {
            if (i > 0) sb.append(", ");
            sb.append(parts.get(i));
        }
        sb.append(" and ").append(parts.get(n - 1));
        return sb.toString();
    }

    public record MarketHealthResult(
        String state,
        int score,
        String summary
    ) {}
}