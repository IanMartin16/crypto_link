
package com.evilink.crypto_link.regime;

import com.evilink.crypto_link.history.PriceHistoryRepository;
import com.evilink.crypto_link.momentum.MomentumService;
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
class RegimeServiceTest {

    private static final String FIAT = "USD";
    private static final String VALID = "price-history-db";
    private static final String INSUFFICIENT = "insufficient-history";
    private static final String INVALID = "invalid-series";

    @Mock
    private TrendService trendService;

    @Mock
    private MomentumService momentumService;

    @Mock
    private PriceHistoryRepository priceHistoryRepo;

    @InjectMocks
    private RegimeService service;

    @Test
    void bullishSignalsShouldProduceBullishRegime() {
        List<String> symbols = List.of("BTC", "ETH");

        mockSignals(symbols,
            List.of(
                trend("BTC", "up", VALID),
                trend("ETH", "up", VALID)
            ),
            List.of(
                momentum("BTC", "up", "medium", VALID),
                momentum("ETH", "up", "medium", VALID)
            )
        );

        var result = service.getRegime(symbols, FIAT);

        assertEquals("bullish", result.state());
        assertBd("1.60", result.score());
        assertBd("1.00", result.confidence());
        assertTrue(result.summary().contains("2 of 2 leaning up"));
    }

    @Test
    void bearishSignalsShouldProduceBearishRegime() {
        List<String> symbols = List.of("BTC", "ETH");

        mockSignals(symbols,
            List.of(
                trend("BTC", "down", VALID),
                trend("ETH", "down", VALID)
            ),
            List.of(
                momentum("BTC", "down", "medium", VALID),
                momentum("ETH", "down", "medium", VALID)
            )
        );

        var result = service.getRegime(symbols, FIAT);

        assertEquals("bearish", result.state());
        assertBd("-1.60", result.score());
        assertBd("1.00", result.confidence());
        assertTrue(result.summary().contains("2 of 2 leaning down"));
    }

    @Test
    void mixedRegimeDoesNotRequireSplitDirections() {
        List<String> symbols = List.of("BTC", "ETH", "SOL");

        mockSignals(symbols,
            List.of(
                trend("BTC", "flat", VALID),
                trend("ETH", "flat", VALID),
                trend("SOL", "flat", VALID)
            ),
            List.of(
                momentum("BTC", "down", "medium", VALID),
                momentum("ETH", "down", "medium", VALID),
                momentum("SOL", "down", "medium", VALID)
            )
        );

        var result = service.getRegime(symbols, FIAT);

        assertEquals("mixed", result.state());
        assertBd("-0.60", result.score());
        assertBd("0.40", result.confidence());
        assertTrue(result.summary().contains("0 up / 3 down of 3"));
        assertFalse(result.summary().contains("Direction is split"));
    }

    @Test
    void oppositeSignalsShouldProduceNeutralRegime() {
        List<String> symbols = List.of("BTC", "ETH");

        mockSignals(symbols,
            List.of(
                trend("BTC", "up", VALID),
                trend("ETH", "down", VALID)
            ),
            List.of(
                momentum("BTC", "up", "high", VALID),
                momentum("ETH", "down", "high", VALID)
            )
        );

        var result = service.getRegime(symbols, FIAT);

        assertEquals("neutral", result.state());
        assertBd("0.00", result.score());
        assertBd("0.00", result.confidence());
        assertTrue(result.summary().contains("1 up / 1 down of 2"));
    }

    @Test
    void insufficientAndInvalidSeriesShouldBeExcluded() {
        List<String> symbols = List.of("BTC", "FLOKI", "MOVE");

        mockSignals(symbols,
            List.of(
                trend("BTC", "up", VALID),
                trend("FLOKI", "flat", INSUFFICIENT),
                trend("MOVE", "flat", INVALID)
            ),
            List.of(
                momentum("BTC", "up", "medium", VALID),
                momentum("FLOKI", "flat", "low", INSUFFICIENT),
                momentum("MOVE", "flat", "low", INVALID)
            )
        );

        var result = service.getRegime(symbols, FIAT);

        // Solo BTC participa: 1 + 0.6 = 1.60
        assertEquals("bullish", result.state());
        assertBd("1.60", result.score());
        assertTrue(result.summary().contains("1 of 1 leaning up"));
    }

    @Test
    void noEvaluableHistoryShouldReturnInsufficientData() {
        List<String> symbols = List.of("LEO", "FLOKI", "MOVE");

        mockSignals(symbols,
            List.of(
                trend("LEO", "flat", INSUFFICIENT),
                trend("FLOKI", "flat", INSUFFICIENT),
                trend("MOVE", "flat", INSUFFICIENT)
            ),
            List.of(
                momentum("LEO", "flat", "low", INSUFFICIENT),
                momentum("FLOKI", "flat", "low", INSUFFICIENT),
                momentum("MOVE", "flat", "low", INSUFFICIENT)
            )
        );

        var result = service.getRegime(symbols, FIAT);

        assertEquals("neutral", result.state());
        assertBd("0.00", result.score());
        assertBd("0.00", result.confidence());
        assertTrue(result.summary().contains(
            "Insufficient valid price history"
        ));
    }

    @Test
    void emptyActiveUniverseShouldNotCalculateSignals() {
        when(priceHistoryRepo.findActiveSymbols(FIAT, 48))
            .thenReturn(List.of());

        var result = service.getUniverseRegime(FIAT);

        assertEquals("neutral", result.state());
        assertBd("0.00", result.score());
        assertBd("0.00", result.confidence());
        assertTrue(result.summary().contains(
            "No recent price observations"
        ));

        verifyNoInteractions(trendService, momentumService);
    }

    private void mockSignals(
        List<String> symbols,
        List<TrendService.TrendRow> trends,
        List<MomentumService.MomentumRow> momentum
    ) {
        when(trendService.getTrends(symbols, FIAT))
            .thenReturn(trends);

        when(momentumService.getMomentum(symbols, FIAT))
            .thenReturn(momentum);
    }

    private TrendService.TrendRow trend(
        String symbol,
        String direction,
        String source
    ) {
        return new TrendService.TrendRow(
            symbol,
            direction,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ONE,
            source
        );
    }

    private MomentumService.MomentumRow momentum(
        String symbol,
        String direction,
        String strength,
        String source
    ) {
        return new MomentumService.MomentumRow(
            symbol,
            direction,
            BigDecimal.ZERO,
            strength,
            BigDecimal.ZERO,
            BigDecimal.ONE,
            source
        );
    }

    private void assertBd(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(
            0,
            new BigDecimal(expected).compareTo(actual),
            "Unexpected BigDecimal value"
        );
    }
}
