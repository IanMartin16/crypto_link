package com.evilink.crypto_link.movers;

import com.evilink.crypto_link.trends.TrendService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
public class MoverService {

    private final TrendService trendService;

    public MoverService(TrendService trendService) {
        this.trendService = trendService;
    }

    public MoversResult getMovers(List<String> symbols, String fiat, int limit) {
        List<TrendService.TrendRow> trends = trendService.getTrends(symbols, fiat);

        List<TrendService.TrendRow> gainers = trends.stream()
            .filter(t -> t.changePct() != null && t.changePct().compareTo(BigDecimal.ZERO) > 0)
            .sorted(Comparator.comparing(TrendService.TrendRow::changePct).reversed())
            .limit(limit)
            .toList();

        List<TrendService.TrendRow> losers = trends.stream()
            .filter(t -> t.changePct() != null && t.changePct().compareTo(BigDecimal.ZERO) < 0)
            .sorted(Comparator.comparing(TrendService.TrendRow::changePct))
            .limit(limit)
            .toList();

        return new MoversResult(gainers, losers);
    }

    public record MoversResult(
        List<TrendService.TrendRow> gainers,
        List<TrendService.TrendRow> losers
    ) {}
}