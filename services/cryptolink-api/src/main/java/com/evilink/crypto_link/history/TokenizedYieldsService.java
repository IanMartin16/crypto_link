package com.evilink.crypto_link.history;

import org.springframework.stereotype.Service;
import java.util.List;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TokenizedYieldsService {
    private final HistoricalPricesRepository repo;
    private volatile List<YieldItem> cache;
    private volatile Instant cachedAt;
    private static final Duration TTL = Duration.ofMinutes(55);

    public TokenizedYieldsService(HistoricalPricesRepository repo) { this.repo = repo; }

    public List<YieldItem> getYields() {
        if (cache != null && cachedAt != null
            && Duration.between(cachedAt, Instant.now()).compareTo(TTL) < 0) return cache;

        var items = repo.findYieldMetrics().stream()
            .map(TokenizedYieldsService::classify)
            .filter(it -> it.status().equals("number") || it.status().equals("accruing"))  // excluded/too_new fuera
            .sorted(Comparator.comparing((YieldItem it) -> it.apy30d() == null)            // números primero…
                              .thenComparing(it -> it.apy30d() == null ? 0.0 : -it.apy30d())) // …por APY desc
            .toList();

        cache = items; cachedAt = Instant.now();
        return items;
    }

        static YieldItem classify(YieldMetrics m) {
        Double apy = null;
        if (m.has30d() > 0 && m.px30() != null && m.px30().signum() > 0) {
            double ratio = m.pxNow().doubleValue() / m.px30().doubleValue();
            apy = (Math.pow(ratio, 365.0 / 30.0) - 1) * 100.0;
        }

        String status;
        if (m.has30d() == 0)                                       status = "too_new";
        else if (m.niveles() < 4)                                  status = m.everUp() ? "accruing" : "excluded";
        else if (apy != null && apy < 0)                           status = "excluded";          // mark-to-market neto
        else if (m.pxP05() != null && m.px30() != null
                 && m.pxP05().doubleValue() < m.px30().doubleValue() * 0.99)
                                                                   status = "excluded";          // dip material >1%
        else if (apy != null && apy >= 0.5 && apy <= 30.0)         status = "number";
        else if (m.everUp())                                       status = "accruing";          // bajo precisión
        else                                                       status = "excluded";          // plano

        Double shown = "number".equals(status) ? Math.round(apy * 10.0) / 10.0 : null;  // 1 decimal
        return new YieldItem(m.symbol(), m.pxNow(), shown, status);
    }
}