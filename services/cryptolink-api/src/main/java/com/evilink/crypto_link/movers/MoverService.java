package com.evilink.crypto_link.movers;

import com.evilink.crypto_link.history.HistoricalPricesRepository;
import com.evilink.crypto_link.trends.TrendService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
public class MoverService {

    private final TrendService trendService;
    private final HistoricalPricesRepository historyRepo;

    public MoverService(TrendService trendService, HistoricalPricesRepository historyRepo) {
        this.trendService = trendService;
        this.historyRepo = historyRepo;
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

    public record UniverseMover(
        String symbol,
        BigDecimal price,
        BigDecimal changePct,
        BigDecimal marketCap,
        BigDecimal volume24h
) {}

/** Resultado del board del universo. */
public record UniverseMoversResult(
        String fiat,
        int windowHours,
        int universeSize,          // cuántos símbolos calificaron (tras filtros)
        List<UniverseMover> gainers,
        List<UniverseMover> losers
) {}

private static final BigDecimal MIN_VOL_24H = new BigDecimal("1000");     // USD
private static final BigDecimal MIN_PRICE   = new BigDecimal("0.0000001");
private static final BigDecimal MAX_ABS_PCT = new BigDecimal("100000");   // 100,000%

/**
 * Movers del universo completo (los ~400 curados), sobre una ventana en horas.
 * 24h funciona hoy; 7d (168h) se "enciende" solo cuando los 50 nuevos cumplan
 * la semana, porque el repo excluye por JOIN a quien no tiene historia suficiente.
 */
public UniverseMoversResult getUniverseMovers(String fiat, int windowHours, int limit) {
    List<HistoricalPricesRepository.MoverRow> rows =
            historyRepo.findUniverseMovers(fiat, windowHours);

    // --- filtro de ruido ---
    List<HistoricalPricesRepository.MoverRow> clean = rows.stream()
            .filter(r -> r.changePct() != null && r.priceNow() != null)
            .filter(r -> r.priceNow().compareTo(MIN_PRICE) >= 0)
            .filter(r -> r.volume24h() == null || r.volume24h().compareTo(MIN_VOL_24H) >= 0)
            .filter(r -> r.changePct().abs().compareTo(MAX_ABS_PCT) <= 0)
            .toList();

    List<UniverseMover> gainers = clean.stream()
            .filter(r -> r.changePct().signum() > 0)
            .sorted(Comparator.comparing(
                    HistoricalPricesRepository.MoverRow::changePct).reversed())
            .limit(limit)
            .map(MoverService::toUniverseMover)
            .toList();

    List<UniverseMover> losers = clean.stream()
            .filter(r -> r.changePct().signum() < 0)
            .sorted(Comparator.comparing(
                    HistoricalPricesRepository.MoverRow::changePct))   // más negativo primero
            .limit(limit)
            .map(MoverService::toUniverseMover)
            .toList();

    return new UniverseMoversResult(fiat, windowHours, clean.size(), gainers, losers);
}

    private static UniverseMover toUniverseMover(HistoricalPricesRepository.MoverRow r) {
        return new UniverseMover(r.symbol(), r.priceNow(), r.changePct(),
                             r.marketCap(), r.volume24h());
    }
}