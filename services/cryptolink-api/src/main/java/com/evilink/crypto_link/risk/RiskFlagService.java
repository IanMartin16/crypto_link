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

        // 1) low confidence regime — expone el % de confianza real
        double regimeConf = regime.confidence().doubleValue();
        if (regimeConf < 0.20) {
            int confPct = (int) Math.round(regimeConf * 100);
            flags.add(new RiskFlag(
                "low_confidence_regime",
                "medium",
                "Low regime confidence",
                "The " + regime.state() + " regime reads at only " + confPct
                    + "% confidence, so directional signals are weakly supported."
            ));
        }

        // 2) insufficient history — dice CUÁNTOS de cuántos
        long insufficientCount = momentum.stream()
            .filter(m -> "insufficient-history".equalsIgnoreCase(m.source()))
            .count();

        if (insufficientCount > 0) {
            int total = symbols.size();
            String assetWord = insufficientCount == 1 ? "asset" : "assets";
            flags.add(new RiskFlag(
                "insufficient_history",
                "medium",
                "Insufficient history",
                insufficientCount + " of " + total + " " + assetWord
                    + " lack enough history for reliable analysis; their signals are excluded."
            ));
        }

        // 3) weak momentum — dice cuántos assets útiles están en 'low'
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
                "Weak momentum",
                "All " + usefulMomentum + " tracked "
                    + (usefulMomentum == 1 ? "asset shows" : "assets show")
                    + " low momentum strength — little conviction behind current moves."
            ));
        }

        // 4) flat market — dice cuántos de cuántos están planos
        long flatTrends = trends.stream()
            .filter(t -> "flat".equalsIgnoreCase(t.direction()))
            .count();

        if (!trends.isEmpty() && flatTrends >= Math.ceil(trends.size() * 0.66)) {
            flags.add(new RiskFlag(
                "flat_market",
                "low",
                "No clear direction",
                flatTrends + " of " + trends.size()
                    + " trend signals are flat — the group is holding without a clear direction."
            ));
        }

        // 5) no clear movers
        if ((movers.gainers() == null || movers.gainers().isEmpty())
            && (movers.losers() == null || movers.losers().isEmpty())) {
            flags.add(new RiskFlag(
                "no_clear_movers",
                "low",
                "No standout movers",
                "No asset is moving far enough from the group to stand out right now."
            ));
        }

        // 6) mixed signals — cuenta CUÁNTOS assets tienen trend vs momentum en conflicto
        long mixedCount = symbols.stream().filter(symbol -> {
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
        }).count();

        if (mixedCount > 0) {
            String assetWord = mixedCount == 1 ? "asset shows" : "assets show";
            flags.add(new RiskFlag(
                "mixed_signals",
                "medium",
                "Conflicting signals",
                mixedCount + " " + assetWord
                    + " trend and momentum pointing opposite ways — direction is unresolved there."
            ));
        }

        String summary = buildSummary(flags);

        return new RiskFlagsResult(flags, summary);
    }

    /**
     * Summary que refleja cuántas alertas y de qué peso, en vez de una frase fija.
     */
    private String buildSummary(List<RiskFlag> flags) {
        if (flags.isEmpty()) {
            return "No risk flags right now — signals are clean across the tracked assets.";
        }

        long medium = flags.stream().filter(f -> "medium".equalsIgnoreCase(f.severity())).count();
        int total = flags.size();
        String flagWord = total == 1 ? "flag" : "flags";

        if (medium > 0) {
            return total + " risk " + flagWord + " active, "
                + medium + " at medium severity — worth a closer look.";
        }
        return total + " low-severity " + flagWord
            + " active — minor caution, nothing pressing.";
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