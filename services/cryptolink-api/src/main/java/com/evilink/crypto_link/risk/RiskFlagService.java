package com.evilink.crypto_link.risk;

import com.evilink.crypto_link.momentum.MomentumService;
import com.evilink.crypto_link.movers.MoverService;
import com.evilink.crypto_link.regime.RegimeService;
import com.evilink.crypto_link.trends.TrendService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RiskFlagService {

    private final RegimeService regimeService;
    private final TrendService trendService;
    private final MomentumService momentumService;
    private final MoverService moverService;

    public RiskFlagService(
        RegimeService regimeService,
        TrendService trendService,
        MomentumService momentumService,
        MoverService moverService
    ) {
        this.regimeService = regimeService;
        this.trendService = trendService;
        this.momentumService = momentumService;
        this.moverService = moverService;
    }

    public RiskFlagsResult getRiskFlags(List<String> symbols, String fiat) {
        var regime = regimeService.getRegime(symbols, fiat);
        var trends = trendService.getTrends(symbols, fiat);
        var momentum = momentumService.getMomentum(symbols, fiat);
        var movers = moverService.getMovers(symbols, fiat, 3);

        List<RiskFlag> flags = new ArrayList<>();

        // 1) low confidence regime
        if (regime.confidence().doubleValue() < 0.20) {
            flags.add(new RiskFlag(
                "low_confidence_regime",
                "medium",
                "Confianza baja en el régimen",
                "El régimen actual no muestra suficiente convicción estadística."
            ));
        }

        // 2) insufficient history
        long insufficientCount = momentum.stream()
            .filter(m -> "insufficient-history".equalsIgnoreCase(m.source()))
            .count();

        if (insufficientCount > 0) {
            flags.add(new RiskFlag(
                "insufficient_history",
                "medium",
                "Histórico insuficiente",
                "Algunos activos aún no tienen suficiente historial para análisis confiable."
            ));
        }

        // 3) weak momentum
        long usefulMomentum = momentum.stream()
            .filter(m -> !"insufficient-history".equalsIgnoreCase(m.source()))
            .count();

        long lowMomentum = momentum.stream()
            .filter(m -> !"insufficient-history".equalsIgnoreCase(m.source()))
            .filter(m -> "low".equalsIgnoreCase(m.strength()))
            .count();

        if (usefulMomentum > 0 && usefulMomentum == lowMomentum) {
            flags.add(new RiskFlag(
                "weak_momentum",
                "low",
                "Momentum débil",
                "Los activos monitoreados muestran fuerza limitada."
            ));
        }

        // 4) flat market
        long flatTrends = trends.stream()
            .filter(t -> "flat".equalsIgnoreCase(t.direction()))
            .count();

        if (!trends.isEmpty() && flatTrends >= Math.ceil(trends.size() * 0.66)) {
            flags.add(new RiskFlag(
                "flat_market",
                "low",
                "Mercado sin dirección clara",
                "La mayoría de las señales de tendencia permanecen estables."
            ));
        }

        // 5) no clear movers
        if ((movers.gainers() == null || movers.gainers().isEmpty())
            && (movers.losers() == null || movers.losers().isEmpty())) {
            flags.add(new RiskFlag(
                "no_clear_movers",
                "low",
                "Sin movers claros",
                "No se observan movimientos relativos suficientemente marcados."
            ));
        }

        // 6) mixed signals
        boolean mixedSignals = symbols.stream().anyMatch(symbol -> {
            var t = trends.stream()
                .filter(x -> x.symbol().equalsIgnoreCase(symbol))
                .findFirst()
                .orElse(null);

            var m = momentum.stream()
                .filter(x -> x.symbol().equalsIgnoreCase(symbol))
                .findFirst()
                .orElse(null);

            if (t == null || m == null) return false;
            if ("insufficient-history".equalsIgnoreCase(m.source())) return false;

            return !t.direction().equalsIgnoreCase(m.direction())
                && !"flat".equalsIgnoreCase(t.direction())
                && !"flat".equalsIgnoreCase(m.direction());
        });

        if (mixedSignals) {
            flags.add(new RiskFlag(
                "mixed_signals",
                "medium",
                "Señales mixtas",
                "Existen discrepancias entre tendencia y momentum en algunos activos."
            ));
        }

        String summary = flags.isEmpty()
            ? "No se detectan alertas relevantes por ahora."
            : "Predominan señales débiles, mixtas o poco concluyentes.";

        return new RiskFlagsResult(flags, summary);
    }

    public record RiskFlag(
        String code,
        String severity,
        String title,
        String detail
    ) {}

    public record RiskFlagsResult(
        List<RiskFlag> flags,
        String summary
    ) {}
}