
package com.evilink.crypto_link.movers;

import com.evilink.crypto_link.history.HistoricalPricesRepository;
import com.evilink.crypto_link.trends.TrendService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MoverServiceTest {

    private static final String FIAT = "USD";

    @Mock
    private TrendService trendService;

    @Mock
    private HistoricalPricesRepository historyRepo;

    @InjectMocks
    private MoverService service;

    @Test
    void invalidAndInsufficientSeriesShouldBeExcluded() {
        var symbols = List.of("BTC", "ETH", "MOVE", "FLOKI");

        when(trendService.getTrends(symbols, FIAT))
            .thenReturn(List.of(
                trend("BTC", "0.40", "price-history-db"),
                trend("ETH", "-0.35", "price-history-db"),
                trend("MOVE", "4.50", "invalid-series"),
                trend("FLOKI", "-5.00", "insufficient-history")
            ));

        var result = service.getMovers(symbols, FIAT, 3);

        assertEquals(1, result.gainers().size());
        assertEquals(1, result.losers().size());

        assertEquals("BTC", result.gainers().get(0).symbol());
        assertEquals("ETH", result.losers().get(0).symbol());

        verifyNoInteractions(historyRepo);
    }

    @Test
    void validMoversShouldBeSortedAndLimited() {
        var symbols = List.of("BTC", "ETH", "SOL", "Q");

        when(trendService.getTrends(symbols, FIAT))
            .thenReturn(List.of(
                trend("BTC", "0.20", "price-history-db"),
                trend("ETH", "1.50", "price-history-db"),
                trend("SOL", "-2.10", "price-history-db"),
                trend("Q", "-0.30", "price-history-db")
            ));

        var result = service.getMovers(symbols, FIAT, 1);

        assertEquals(1, result.gainers().size());
        assertEquals(1, result.losers().size());

        assertEquals("ETH", result.gainers().get(0).symbol());
        assertEquals("SOL", result.losers().get(0).symbol());

        verifyNoInteractions(historyRepo);
    }

    private TrendService.TrendRow trend(
        String symbol,
        String change,
        String source
    ) {
        BigDecimal pct = new BigDecimal(change);

        String direction =
            pct.signum() > 0 ? "up" :
            pct.signum() < 0 ? "down" : "flat";

        return new TrendService.TrendRow(
            symbol,
            direction,
            pct,
            pct.abs(),
            BigDecimal.ONE,
            source
        );
    }
}
