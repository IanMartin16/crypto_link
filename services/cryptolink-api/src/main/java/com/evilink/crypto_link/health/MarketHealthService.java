package com.evilink.crypto_link.health;

import com.evilink.crypto_link.anomaly.AnomalyService;
import com.evilink.crypto_link.momentum.MomentumService;
import com.evilink.crypto_link.regime.RegimeService;
import com.evilink.crypto_link.risk.RiskFlagService;
import org.springframework.stereotype.Service;

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

        // 1) Regime confidence
        double confidence = regime.confidence().doubleValue();
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

        String summary = switch (state) {
            case "healthy" -> "El mercado mantiene señales saludables y buena estabilidad relativa.";
            case "stable" -> "El mercado se mantiene estable, aunque con algunas señales de atención.";
            case "fragile" -> "El mercado muestra señales débiles y convicción limitada.";
            default -> "El mercado opera bajo presión y requiere atención adicional.";
        };

        return new MarketHealthResult(state, score, summary);
    }

    public record MarketHealthResult(
        String state,
        int score,
        String summary
    ) {}
}