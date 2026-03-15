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
            : trends.stream().mapToDouble(t -> Math.abs(t.score().doubleValue())).average().orElse(0.0);

        double momentumAvg = momentum.stream()
            .filter(m -> !"insufficient-history".equalsIgnoreCase(m.source()))
            .mapToDouble(m -> Math.abs(m.score().doubleValue()))
            .average()
            .orElse(0.0);

        int moverCount = (movers.gainers() == null ? 0 : movers.gainers().size())
            + (movers.losers() == null ? 0 : movers.losers().size());

        double regimeConfidence = regime.confidence().doubleValue();

        int score = 0;
        score += Math.min(35, (int) Math.round(trendAvg * 20));
        score += Math.min(25, (int) Math.round(momentumAvg * 30));
        score += Math.min(20, moverCount * 5);
        score += Math.min(20, (int) Math.round(regimeConfidence * 20));

        score = Math.max(0, Math.min(100, score));
        
        String state = deriveState(regime.state(), trendAvg, momentumAvg, moverCount);

        List<String> topAssets = buildTopAssets(trends, momentum, movers);
        List<String> tags = buildTags(state, trendAvg, momentumAvg, moverCount, regimeConfidence);

        boolean hasAnySignal = 
            trendAvg > 0.0 || momentumAvg > 0.0 || moverCount > 0 || regimeConfidence > 0.0 || !topAssets.isEmpty();

            if (hasAnySignal) {
                score = Math.max(score, 12);
            }

        String summary = buildSummary(state, topAssets, score, tags);

        return new SocialPulseResult(state, score, summary, topAssets, tags);
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

    private List<String> buildTags(String state, double trendAvg, double momentumAvg, int moverCount, double confidence) {
        List<String> tags = new ArrayList<>();

        if ("bullish".equalsIgnoreCase(state)) tags.add("bullish narrative");
        if ("bearish".equalsIgnoreCase(state)) tags.add("risk-off tone");
        if ("mixed".equalsIgnoreCase(state)) tags.add("mixed leadership");
        if ("neutral".equalsIgnoreCase(state)) tags.add("low narrative intensity");

        if (momentumAvg > 0.5) tags.add("high momentum");
        else if (momentumAvg > 0.2) tags.add("moderate momentum");
        else tags.add("weak momentum");

        if (moverCount >= 4) tags.add("broad activity");
        else if (moverCount >= 2) tags.add("selective attention");
        else tags.add("low breadth");

        if (confidence < 0.15) tags.add("low conviction");
        else if (confidence > 0.5) tags.add("strong conviction");

        return tags.stream().distinct().limit(4).collect(Collectors.toList());
    }

    private String buildSummary(String state, List<String> topAssets, int score, List<String> tags) {
        String assets = topAssets.isEmpty() ? "core assets" : String.join(", ", topAssets);
        String tagLead = tags.isEmpty() ? "mixed conditions" : tags.get(0);

        return switch (state.toLowerCase()) {
            case "bullish" ->
                "Social attention is tilted bullish around " + assets + ", with " + tagLead + " and pulse score " + score + ".";
            case "bearish" ->
                "Social attention is tilted bearish around " + assets + ", with " + tagLead + " and pulse score " + score + ".";
            case "mixed" ->
                "Social attention is mixed across " + assets + ", with " + tagLead + " and pulse score " + score + ".";
            default ->
                "Social attention remains neutral, centered on " + assets + ", with " + tagLead + " and pulse score " + score + ".";
        };
    }

    public record SocialPulseResult(
        String state,
        int score,
        String summary,
        List<String> topAssets,
        List<String> tags
    ) {}
}