
package com.evilink.crypto_link.momentum;

import com.evilink.crypto_link.history.PriceHistoryRepository;
import com.evilink.crypto_link.history.PriceHistoryRepository.PricePointRow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MomentumServiceTest {

    @Mock
    private PriceHistoryRepository repository;

    @InjectMocks
    private MomentumService service;

    private static final String FIAT = "USD";
    private static final int POINTS = 24;

    private static final OffsetDateTime BASE =
        OffsetDateTime.parse("2026-11-01T12:00:00Z");

    @BeforeEach
    void setUp() {
        // MockitoExtension inicializa los mocks.
    }

    // 1. Movimiento ascendente consistente
    @Test
    void continuousUptrendShouldHaveFullMomentum() {

        mockSeries("SOL",
            "100", "100.5", "101", "101.5", "102");

        var row = service.getMomentum(
            List.of("SOL"), FIAT
        ).get(0);

        assertEquals("up", row.direction());
        assertBd("2.00", row.changePct());
        assertBd("2.00", row.score());
        assertEquals("high", row.strength());
        assertEquals("price-history-db", row.source());
    }

    // 2. Misma variacion, menor consistencia
    @Test
    void irregularUptrendShouldReduceMomentumScore() {

        mockSeries("SOL",
            "100", "101", "100.5", "102.5", "102");

        var row = service.getMomentum(
            List.of("SOL"), FIAT
        ).get(0);

        // Cambio neto: +2%
        // Pasos favorables: 2 de 4
        // Score esperado: 2 * 0.5 = 1.00

        assertEquals("up", row.direction());
        assertBd("2.00", row.changePct());
        assertBd("1.00", row.score());
        assertEquals("high", row.strength());
    }

    // 3. Precio microscopico sin redondeo
    @Test
    void microscopicPriceShouldPreservePrecision() {

        mockSeries("ASTRO",
            "0.000008390",
            "0.000007500",
            "0.000006712");

        var row = service.getMomentum(
            List.of("ASTRO"), FIAT
        ).get(0);

        assertEquals("down", row.direction());
        assertBd("-20.00", row.changePct());
        assertBd("20.00", row.score());
        assertBd("0.000006712", row.last());
        assertEquals("high", row.strength());
        assertEquals("price-history-db", row.source());
    }

    // 4. Historia insuficiente
    @Test
    void insufficientHistoryShouldNotProduceSignal() {

        mockSeries("FLOKI",
            "0.000042", "0.000043");

        var row = service.getMomentum(
            List.of("FLOKI"), FIAT
        ).get(0);

        assertEquals("flat", row.direction());
        assertBd("0", row.changePct());
        assertBd("0", row.score());
        assertEquals("low", row.strength());
        assertEquals("insufficient-history", row.source());

        // Se conserva el ultimo precio disponible.
        assertBd("0.000043", row.last());
    }

    // 5. Precio intermedio invalido
    @Test
    void zeroPriceShouldInvalidateSeries() {

        mockSeries("RSR",
            "100", "0", "102");

        var row = service.getMomentum(
            List.of("RSR"), FIAT
        ).get(0);

        assertEquals("invalid-series", row.source());
        assertEquals("flat", row.direction());
        assertBd("0", row.score());
        assertEquals("low", row.strength());
    }

    // 6. Timestamp intermedio nulo
    @Test
    void nullTimestampShouldInvalidateSeries() {

        List<PricePointRow> rows = series(
            "100", "101", "102");

        PricePointRow middle = rows.get(1);

        rows.set(1, new PricePointRow(
            middle.price(),
            middle.change24h(),
            middle.marketCap(),
            null
        ));

        when(repository.findSeries(
            FIAT, "BTC", POINTS
        )).thenReturn(rows);

        var row = service.getMomentum(
            List.of("BTC"), FIAT
        ).get(0);

        assertEquals("invalid-series", row.source());
        assertBd("0", row.score());
    }

    // 7. Serie estable, pero valida
    @Test
    void stableSeriesShouldHaveZeroMomentum() {

        mockSeries("APEPE",
            "0.0000012",
            "0.0000012",
            "0.0000012");

        var row = service.getMomentum(
            List.of("APEPE"), FIAT
        ).get(0);

        assertEquals("flat", row.direction());
        assertBd("0", row.changePct());
        assertBd("0", row.score());
        assertEquals("low", row.strength());
        assertBd("0.0000012", row.last());
        assertEquals("price-history-db", row.source());
    }

    // 8. Simbolo sin ninguna observacion
    @Test
    void missingHistoryShouldReturnInsufficientHistory() {

        when(repository.findSeries(
            FIAT, "UNKNOWN", POINTS
        )).thenReturn(List.of());

        var row = service.getMomentum(
            List.of("UNKNOWN"), FIAT
        ).get(0);

        assertEquals("insufficient-history", row.source());
        assertNull(row.last());
        assertBd("0", row.score());
    }

    // Helpers

    private void mockSeries(
        String symbol,
        String... prices
    ) {
        when(repository.findSeries(
            FIAT, symbol, POINTS
        )).thenReturn(series(prices));
    }

    private List<PricePointRow> series(
        String... prices
    ) {
        List<PricePointRow> rows = new ArrayList<>();

        // El repositorio devuelve DESC:
        // observacion mas reciente primero.
        for (int i = prices.length - 1; i >= 0; i--) {
            rows.add(new PricePointRow(
                new BigDecimal(prices[i]),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BASE.plusMinutes(i)
            ));
        }

        return rows;
    }

    private void assertBd(
        String expected,
        BigDecimal actual
    ) {
        assertNotNull(actual);
        assertEquals(
            0,
            new BigDecimal(expected).compareTo(actual),
            "Valor BigDecimal inesperado"
        );
    }
}
