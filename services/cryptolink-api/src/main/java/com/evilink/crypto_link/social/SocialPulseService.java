package com.evilink.crypto_link.social;

import com.evilink.crypto_link.momentum.MomentumService;
import com.evilink.crypto_link.movers.MoverService;
import com.evilink.crypto_link.regime.RegimeService;
import com.evilink.crypto_link.trends.TrendService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class SocialPulseService {

    private final TrendService trendService;
    private final MomentumService momentumService;
    private final MoverService moverService;
    private final RegimeService regimeService;

    public SocialPulseService(
        TrendService trendService,
        MomentumService momentumService,
        MoverService moverService,
        RegimeService regimeService
    ) {
        this.trendService = trendService;
        this.momentumService = momentumService;
        this.moverService = moverService;
        this.regimeService = regimeService;
    }

    public SocialPulseResult getSocialPulse(List<String> symbols, String fiat) {
        var trends = trendService.getTrends(symbols, fiat);
        var momentum = momentumService.getMomentum(symbols, fiat);
        var movers = moverService.getMovers(symbols, fiat, 3);
        var regime = regimeService.getRegime(symbols, fiat);

        double trendAvg = trends.isEmpty()
            ? 0.0
            : trends.stream()
                .mapToDouble(t -> Math.abs(t.score().doubleValue()))
                .average()
                .orElse(0.0);

        double momentumAvg = momentum.stream()
            .filter(m -> !"insufficient-history".equalsIgnoreCase(m.source()))
            .mapToDouble(m -> Math.abs(m.score().doubleValue()))
            .average()
            .orElse(0.0);

        int moverCount = (movers.gainers() == null ? 0 : movers.gainers().size())
            + (movers.losers() == null ? 0 : movers.losers().size());

        double regimeConfidence = regime.confidence().doubleValue();

        String state = deriveState(regime.state(), trendAvg, momentumAvg, moverCount);
        String breadth = deriveBreadth(moverCount, trends.size());
        String conviction = deriveConviction(regimeConfidence, trendAvg, momentumAvg);
        String leadership = deriveLeadership(trends, momentum);

        List<String> topAssets = buildTopAssets(trends, momentum, movers);
        List<String> tags = buildTags(state, breadth, conviction, leadership, trendAvg, momentumAvg);

        int score = 0;
        score += Math.min(35, (int) Math.round(trendAvg * 20));
        score += Math.min(25, (int) Math.round(momentumAvg * 30));
        score += Math.min(20, moverCount * 5);
        score += Math.min(20, (int) Math.round(regimeConfidence * 20));

        score = Math.max(0, Math.min(100, score));

        boolean hasAnySignal =
            trendAvg > 0.0 ||
            momentumAvg > 0.0 ||
            moverCount > 0 ||
            regimeConfidence > 0.0 ||
            !topAssets.isEmpty();

        if (hasAnySignal) {
            score = Math.max(score, 12);
        }

        String summary = buildSummary(state, breadth, conviction, leadership, topAssets, score);

        return new SocialPulseResult(
            state,
            score,
            breadth,
            conviction,
            leadership,
            summary,
            topAssets,
            tags
        );
    }

    private String deriveState(String regimeState, double trendAvg, double momentumAvg, int moverCount) {
        if ("bullish".equalsIgnoreCase(regimeState) && (trendAvg > 0.6 || momentumAvg > 0.4)) {
            return "bullish";
        }
        if ("bearish".equalsIgnoreCase(regimeState) && (trendAvg > 0.6 || momentumAvg > 0.4)) {
            return "bearish";
        }
        if ("mixed".equalsIgnoreCase(regimeState) || moverCount >= 3) {
            return "mixed";
        }
        return "neutral";
    }

    private String deriveBreadth(int moverCount, int trendCount) {
        if (trendCount <= 0) return "low";
        if (moverCount >= 4) return "broad";
        if (moverCount >= 2) return "selective";
        return "low";
    }

    private String deriveConviction(double regimeConfidence, double trendAvg, double momentumAvg) {
        double combined = (regimeConfidence + trendAvg + momentumAvg) / 3.0;

        if (combined >= 0.55) return "strong";
        if (combined >= 0.25) return "moderate";
        return "low";
    }

    private String deriveLeadership(
        List<TrendService.TrendRow> trends,
        List<MomentumService.MomentumRow> momentum
    ) {
        List<Double> trendScores = trends.stream()
            .map(t -> Math.abs(t.score().doubleValue()))
            .sorted(Comparator.reverseOrder())
            .toList();

        List<Double> momentumScores = momentum.stream()
            .filter(m -> !"insufficient-history".equalsIgnoreCase(m.source()))
            .map(m -> Math.abs(m.score().doubleValue()))
            .sorted(Comparator.reverseOrder())
            .toList();

        double topTrend = trendScores.isEmpty() ? 0.0 : trendScores.get(0);
        double secondTrend = trendScores.size() > 1 ? trendScores.get(1) : 0.0;

        double topMomentum = momentumScores.isEmpty() ? 0.0 : momentumScores.get(0);
        double secondMomentum = momentumScores.size() > 1 ? momentumScores.get(1) : 0.0;

        boolean concentrated =
            (topTrend > 0.0 && secondTrend > 0.0 && topTrend >= secondTrend * 1.8) ||
            (topMomentum > 0.0 && secondMomentum > 0.0 && topMomentum >= secondMomentum * 1.8);

        boolean distributed =
            trendScores.size() >= 3 &&
            momentumScores.size() >= 2 &&
            topTrend > 0.0 &&
            secondTrend > 0.0 &&
            topTrend < secondTrend * 1.35;

        if (concentrated) return "concentrated";
        if (distributed) return "distributed";
        return "mixed";
    }

    private List<String> buildTopAssets(
        List<TrendService.TrendRow> trends,
        List<MomentumService.MomentumRow> momentum,
        MoverService.MoversResult movers
    ) {
        LinkedHashSet<String> out = new LinkedHashSet<>();

        trends.stream()
            .sorted((a, b) -> Double.compare(Math.abs(b.score().doubleValue()), Math.abs(a.score().doubleValue())))
            .limit(2)
            .map(TrendService.TrendRow::symbol)
            .forEach(out::add);

        momentum.stream()
            .filter(m -> !"insufficient-history".equalsIgnoreCase(m.source()))
            .sorted((a, b) -> Double.compare(Math.abs(b.score().doubleValue()), Math.abs(a.score().doubleValue())))
            .limit(2)
            .map(MomentumService.MomentumRow::symbol)
            .forEach(out::add);

        Stream.concat(
                movers.gainers() == null ? Stream.empty() : movers.gainers().stream(),
                movers.losers() == null ? Stream.empty() : movers.losers().stream()
            )
            .limit(2)
            .map(TrendService.TrendRow::symbol)
            .forEach(out::add);

        return out.stream().limit(3).collect(Collectors.toList());
    }

    private List<String> buildTags(
        String state,
        String breadth,
        String conviction,
        String leadership,
        double trendAvg,
        double momentumAvg
    ) {
        List<String> tags = new ArrayList<>();

        if ("bullish".equalsIgnoreCase(state)) tags.add("bullish narrative");
        if ("bearish".equalsIgnoreCase(state)) tags.add("risk-off tone");
        if ("mixed".equalsIgnoreCase(state)) tags.add("mixed sentiment");
        if ("neutral".equalsIgnoreCase(state)) tags.add("low narrative intensity");

        if (momentumAvg > 0.5) tags.add("high momentum");
        else if (momentumAvg > 0.2) tags.add("moderate momentum");
        else tags.add("weak momentum");

        if ("broad".equalsIgnoreCase(breadth)) tags.add("broad participation");
        else if ("selective".equalsIgnoreCase(breadth)) tags.add("selective attention");
        else tags.add("low breadth");

        if ("strong".equalsIgnoreCase(conviction)) tags.add("strong conviction");
        else if ("moderate".equalsIgnoreCase(conviction)) tags.add("moderate conviction");
        else tags.add("low conviction");

        if ("concentrated".equalsIgnoreCase(leadership)) tags.add("concentrated leadership");
        else if ("distributed".equalsIgnoreCase(leadership)) tags.add("distributed leadership");

        return tags.stream().distinct().limit(5).collect(Collectors.toList());
    }

    private String buildSummary(
        String state,
        String breadth,
        String conviction,
        String leadership,
        List<String> topAssets,
        int score
    ) {
        String assets = topAssets.isEmpty() ? "core assets" : String.join(", ", topAssets);

        String breadthText = switch (breadth.toLowerCase()) {
            case "broad" -> "broad participation";
            case "selective" -> "selective participation";
            default -> "low participation";
        };

        String convictionText = switch (conviction.toLowerCase()) {
            case "strong" -> "strong conviction";
            case "moderate" -> "moderate conviction";
            default -> "low conviction";
        };

        String leadershipText = switch (leadership.toLowerCase()) {
            case "concentrated" -> "concentrated leadership";
            case "distributed" -> "distributed leadership";
            default -> "mixed leadership";
        };

        return switch (state.toLowerCase()) {
            case "bullish" ->
                "Attention is clustering around " + assets + ", with " + breadthText + ", " + convictionText + ", and " + leadershipText + ". Pulse score " + score + ".";
            case "bearish" ->
                "Attention is turning defensive around " + assets + ", with " + breadthText + ", " + convictionText + ", and " + leadershipText + ". Pulse score " + score + ".";
            case "mixed" ->
                "Attention is mixed across " + assets + ", with " + breadthText + ", " + convictionText + ", and " + leadershipText + ". Pulse score " + score + ".";
            default ->
                "Attention remains neutral around " + assets + ", with " + breadthText + ", " + convictionText + ", and " + leadershipText + ". Pulse score " + score + ".";
        };
    }

    public record SocialPulseResult(
        String state,
        int score,
        String breadth,
        String conviction,
        String leadership,
        String summary,
        List<String> topAssets,
        List<String> tags
    ) {}
}