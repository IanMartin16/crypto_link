
package com.evilink.crypto_link.trends;

import com.evilink.crypto_link.history.PriceHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrendServiceTest {

    @Mock
    private PriceHistoryRepository repository;

    @InjectMocks
    private TrendService trendService;

    private static final OffsetDateTime BASE =
        OffsetDateTime.parse("2026-10-31T18:00:00Z");

    private PriceHistoryRepository.PricePointRow point(
        String price,
        OffsetDateTime timestamp
    ) {
        return new PriceHistoryRepository.PricePointRow(
            price == null ? null : new BigDecimal(price),
            null,
            null,
            timestamp
        );
    }

    @Test
    void shouldPreserveMicroscopicPricePrecision() {

        // DESC: precio más reciente primero.
        when(repository.findSeries("USD", "ASTRO", 24))
            .thenReturn(List.of(
                point("0.000006712", BASE.plusMinutes(2)),
                point("0.000007000", BASE.plusMinutes(1)),
                point("0.000008390", BASE)
            ));

        var result = trendService
            .getTrends(List.of("ASTRO"), "USD")
            .get(0);

        assertEquals("down", result.direction());
        assertEquals(
            new BigDecimal("-20.00"),
            result.changePct()
        );
        assertEquals(
            new BigDecimal("20.00"),
            result.score()
        );
        assertEquals(
            new BigDecimal("0.000006712"),
            result.last()
        );
        assertEquals("price-history-db", result.source());
    }

    @Test
    void shouldRejectNegativeLatestPrice() {

        when(repository.findSeries("USD", "TEST", 24))
            .thenReturn(List.of(
                point("-0.000008390", BASE.plusMinutes(2)),
                point("0.000009000", BASE.plusMinutes(1)),
                point("0.000010000", BASE)
            ));

        var result = trendService
            .getTrends(List.of("TEST"), "USD")
            .get(0);

        assertEquals("invalid-series", result.source());
        assertEquals("flat", result.direction());
        assertEquals(0, result.score().signum());
        assertNull(result.last());
    }

    @Test
    void shouldRejectInvalidIntermediateTimestamp() {

        when(repository.findSeries("USD", "TEST", 24))
            .thenReturn(List.of(
                point("0.000008390", BASE.plusMinutes(2)),
                point("0.000009000", null),
                point("0.000010000", BASE)
            ));

        var result = trendService
            .getTrends(List.of("TEST"), "USD")
            .get(0);

        assertEquals("invalid-series", result.source());
        assertEquals(0, result.score().signum());
    }

    @Test
    void shouldRejectZeroPrice() {

        when(repository.findSeries("USD", "TEST", 24))
            .thenReturn(List.of(
                point("0.000008390", BASE.plusMinutes(2)),
                point("0", BASE.plusMinutes(1)),
                point("0.000010000", BASE)
            ));

        var result = trendService
            .getTrends(List.of("TEST"), "USD")
            .get(0);

        assertEquals("invalid-series", result.source());
    }

    @Test
    void shouldHandleNonexistentSymbol() {

        when(repository.findSeries("USD", "UNKNOWN", 24))
            .thenReturn(List.of());

        var result = trendService
            .getTrends(List.of("UNKNOWN"), "USD")
            .get(0);

        assertEquals("insufficient-history", result.source());
        assertEquals("flat", result.direction());
        assertEquals(0, result.score().signum());
        assertNull(result.last());
    }

    @Test
    void shouldHandleOnlyTwoObservations() {

        when(repository.findSeries("USD", "APEPE", 24))
            .thenReturn(List.of(
                point("0.00000119", BASE.plusMinutes(1)),
                point("0.00000120", BASE)
            ));

        var result = trendService
            .getTrends(List.of("APEPE"), "USD")
            .get(0);

        assertEquals("insufficient-history", result.source());
        assertEquals(
            new BigDecimal("0.00000119"),
            result.last()
        );
    }

    @Test
    void shouldPreserveOriginalTrendCalculation() {

        when(repository.findSeries("USD", "SOL", 24))
            .thenReturn(List.of(
                point("102", BASE.plusMinutes(2)),
                point("101", BASE.plusMinutes(1)),
                point("100", BASE)
            ));

        var result = trendService
            .getTrends(List.of("SOL"), "USD")
            .get(0);

        assertEquals("up", result.direction());
        assertEquals(
            new BigDecimal("2.00"),
            result.changePct()
        );
        assertEquals(
            new BigDecimal("2.00"),
            result.score()
        );
    }
}
