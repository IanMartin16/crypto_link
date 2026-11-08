
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

    private static final String VALID_SOURCE = "price-history-db";

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

    public RiskFlagsResult getRiskFlags(
        List<String> symbols,
        String fiat
    ) {
        if (symbols == null || symbols.isEmpty()) {
            return new RiskFlagsResult(
                List.of(),
                "No assets supplied for evaluation."
            );
        }

        var regime = regimeService.getRegime(symbols, fiat);
        var trends = trendService.getTrends(symbols, fiat);
        var momentum = momentumService.getMomentum(symbols, fiat);
        var movers = moverService.getMovers(symbols, fiat, 3);

        List<RiskFlag> flags = new ArrayList<>();

        // Solo observaciones validas.
        var validTrends = trends.stream()
            .filter(t -> validSource(t.source()))
            .toList();

        var validMomentum = momentum.stream()
            .filter(m -> validSource(m.source()))
            .toList();

        // Simbolos evaluables por ambos servicios.
        long evaluablePairs = symbols.stream()
            .filter(symbol -> {
                boolean hasTrend = validTrends.stream()
                    .anyMatch(t ->
                        t.symbol().equalsIgnoreCase(symbol));

                boolean hasMomentum = validMomentum.stream()
                    .anyMatch(m ->
                        m.symbol().equalsIgnoreCase(symbol));

                return hasTrend && hasMomentum;
            })
            .count();

        // 1) Aggregate directional bias.
        // Conservamos el codigo y el umbral existentes.
        // Regime confidence NO mide calidad estadistica.
        double regimeConf = regime.confidence().doubleValue();

        if (evaluablePairs > 0 && regimeConf < 0.20) {
            int confPct = (int) Math.round(regimeConf * 100);

            flags.add(new RiskFlag(
                "low_confidence_regime",
                "medium",
                "Low aggregate directional bias",
                "The aggregate regime has a directional bias " +
                "magnitude of " + confPct +
                "%. This does not measure data reliability."
            ));
        }

        // 2) Insufficient or invalid history.
        // Un simbolo requiere Trend y Momentum validos.
        long unavailableCount =
            symbols.size() - evaluablePairs;

        if (unavailableCount > 0) {
            flags.add(new RiskFlag(
                "insufficient_history",
                "medium",
                "Insufficient or invalid history",
                unavailableCount + " of " + symbols.size() +
                " assets cannot be fully evaluated because " +
                "their trend or momentum history is " +
                "insufficient or invalid."
            ));
        }

        // 3) Weak momentum.
        // Excluir insufficient-history e invalid-series.
        long usefulMomentum = validMomentum.size();

        long lowMomentum = validMomentum.stream()
            .filter(m ->
                "low".equalsIgnoreCase(m.strength()))
            .count();

        if (usefulMomentum > 0 &&
            usefulMomentum == lowMomentum) {

            flags.add(new RiskFlag(
                "weak_momentum",
                "low",
                "Weak momentum",
                "All " + usefulMomentum + " evaluable " +
                (usefulMomentum == 1
                    ? "asset shows"
                    : "assets show") +
                " low momentum strength."
            ));
        }

        // 4) Flat market.
        // Solo tendencias validas en numerador
        // y denominador.
        long flatTrends = validTrends.stream()
            .filter(t ->
                "flat".equalsIgnoreCase(t.direction()))
            .count();

        if (!validTrends.isEmpty() &&
            flatTrends >= Math.ceil(
                validTrends.size() * 0.66
            )) {

            flags.add(new RiskFlag(
                "flat_market",
                "low",
                "No clear direction",
                flatTrends + " of " +
                validTrends.size() +
                " evaluable trend signals are flat."
            ));
        }

        // 5) No directional movers.
        // No emitir una conclusion sin datos validos.
        boolean noGainers =
            movers.gainers() == null ||
            movers.gainers().isEmpty();

        boolean noLosers =
            movers.losers() == null ||
            movers.losers().isEmpty();

        if (!validTrends.isEmpty() &&
            noGainers && noLosers) {

            flags.add(new RiskFlag(
                "no_clear_movers",
                "low",
                "No directional movers",
                "No positive or negative price changes " +
                "were identified among the evaluable trends."
            ));
        }

        // 6) Mixed signals.
        // Ambas fuentes deben ser validas.
        long mixedCount = symbols.stream()
            .filter(symbol -> {

                var t = validTrends.stream()
                    .filter(x ->
                        x.symbol().equalsIgnoreCase(symbol))
                    .findFirst()
                    .orElse(null);

                var m = validMomentum.stream()
                    .filter(x ->
                        x.symbol().equalsIgnoreCase(symbol))
                    .findFirst()
                    .orElse(null);

                if (t == null || m == null) {
                    return false;
                }

                return !t.direction().equalsIgnoreCase(
                        m.direction())
                    && !"flat".equalsIgnoreCase(
                        t.direction())
                    && !"flat".equalsIgnoreCase(
                        m.direction());
            })
            .count();

        if (mixedCount > 0) {
            flags.add(new RiskFlag(
                "mixed_signals",
                "medium",
                "Conflicting signals",
                mixedCount + " " +
                (mixedCount == 1
                    ? "asset shows"
                    : "assets show") +
                " trend and momentum pointing " +
                "in opposite directions."
            ));
        }

        return new RiskFlagsResult(
            flags,
            buildSummary(flags)
        );
    }

    private boolean validSource(String source) {
        return VALID_SOURCE.equalsIgnoreCase(source);
    }

    private String buildSummary(List<RiskFlag> flags) {
        if (flags.isEmpty()) {
            return "No diagnostic flags triggered " +
                "for the evaluable assets.";
        }

        long medium = flags.stream()
            .filter(f ->
                "medium".equalsIgnoreCase(f.severity()))
            .count();

        int total = flags.size();

        String flagWord =
            total == 1 ? "flag" : "flags";

        if (medium > 0) {
            return total + " risk " + flagWord +
                " active, " + medium +
                " at medium severity — worth a closer look.";
        }

        return total + " low-severity " +
            flagWord +
            " active — minor caution, nothing pressing.";
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
