
package com.evilink.crypto_link.risk;

import com.evilink.crypto_link.momentum.MomentumService;
import com.evilink.crypto_link.movers.MoverService;
import com.evilink.crypto_link.regime.RegimeService;
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
class RiskFlagServiceTest {

    private static final String FIAT = "USD";
    private static final String VALID = "price-history-db";
    private static final String INSUFFICIENT = "insufficient-history";
    private static final String INVALID = "invalid-series";

    @Mock
    private RegimeService regimeService;

    @Mock
    private TrendService trendService;

    @Mock
    private MomentumService momentumService;

    @Mock
    private MoverService moverService;

    @InjectMocks
    private RiskFlagService service;

    // 1. No convertir falta de historia en señales de mercado.
    @Test
    void insufficientHistoryShouldProduceOnlyCoverageFlag() {
        var symbols = List.of("LEO", "MOVE", "FLOKI");

        mockAnalysis(
            symbols,
            List.of(
                trend("LEO", "flat", INSUFFICIENT),
                trend("MOVE", "flat", INSUFFICIENT),
                trend("FLOKI", "flat", INSUFFICIENT)
            ),
            List.of(
                momentum("LEO", "flat", "low", INSUFFICIENT),
                momentum("MOVE", "flat", "low", INSUFFICIENT),
                momentum("FLOKI", "flat", "low", INSUFFICIENT)
            ),
            "0.00"
        );

        var result = service.getRiskFlags(symbols, FIAT);

        assertEquals(1, result.flags().size());
        assertHasFlag(result, "insufficient_history");
        assertNoFlag(result, "low_confidence_regime");
        assertNoFlag(result, "flat_market");
        assertNoFlag(result, "weak_momentum");
        assertNoFlag(result, "no_clear_movers");
    }

    // 2. Una serie invalida tampoco es una señal valida.
    @Test
    void invalidSeriesShouldBeExcludedFromDiagnostics() {
        var symbols = List.of("MOVE");

        mockAnalysis(
            symbols,
            List.of(trend("MOVE", "flat", INVALID)),
            List.of(momentum("MOVE", "flat", "low", INVALID)),
            "0.00"
        );

        var result = service.getRiskFlags(symbols, FIAT);

        assertEquals(1, result.flags().size());
        assertHasFlag(result, "insufficient_history");
        assertNoFlag(result, "weak_momentum");
        assertNoFlag(result, "flat_market");
        assertNoFlag(result, "no_clear_movers");
    }

    // 3. El denominador debe excluir los simbolos invalidos.
    @Test
    void partialCoverageShouldUseOnlyValidTrends() {
        var symbols = List.of("BTC", "ETH", "SOL");

        mockAnalysis(
            symbols,
            List.of(
                trend("BTC", "flat", VALID),
                trend("ETH", "flat", INVALID),
                trend("SOL", "up", VALID)
            ),
            List.of(
                momentum("BTC", "flat", "medium", VALID),
                momentum("ETH", "flat", "low", INVALID),
                momentum("SOL", "up", "medium", VALID)
            ),
            "0.50"
        );

        var result = service.getRiskFlags(symbols, FIAT);

        // Solo 1 de 2 Trends validos es flat: 50%.
        assertHasFlag(result, "insufficient_history");
        assertNoFlag(result, "flat_market");
        assertNoFlag(result, "weak_momentum");
    }

    // 4. Momentum bajo real debe conservar su alerta.
    @Test
    void validLowMomentumShouldTriggerWeakMomentum() {
        var symbols = List.of("BTC", "ETH");

        mockAnalysis(
            symbols,
            List.of(
                trend("BTC", "up", VALID),
                trend("ETH", "up", VALID)
            ),
            List.of(
                momentum("BTC", "up", "low", VALID),
                momentum("ETH", "up", "low", VALID)
            ),
            "0.50"
        );

        var result = service.getRiskFlags(symbols, FIAT);

        assertEquals(1, result.flags().size());
        assertHasFlag(result, "weak_momentum");
    }

    // 5. Dos de tres Trends validos superan el 66%.
    @Test
    void twoOfThreeFlatTrendsShouldTriggerFlatMarket() {
        var symbols = List.of("Q", "REZ", "ONT");

        mockAnalysis(
            symbols,
            List.of(
                trend("Q", "flat", VALID),
                trend("REZ", "flat", VALID),
                trend("ONT", "up", VALID)
            ),
            List.of(
                momentum("Q", "flat", "medium", VALID),
                momentum("REZ", "flat", "medium", VALID),
                momentum("ONT", "up", "medium", VALID)
            ),
            "0.50"
        );

        var result = service.getRiskFlags(symbols, FIAT);

        assertHasFlag(result, "flat_market");

        var flag = findFlag(result, "flat_market");

        assertTrue(flag.detail().contains(
            "2 of 3 evaluable trend signals"
        ));
    }

    // 6. Un flat de tres no alcanza el umbral.
    @Test
    void oneOfThreeFlatTrendsShouldNotTriggerFlatMarket() {
        var symbols = List.of("BTC", "ETH", "SOL");

        mockAnalysis(
            symbols,
            List.of(
                trend("BTC", "flat", VALID),
                trend("ETH", "up", VALID),
                trend("SOL", "down", VALID)
            ),
            List.of(
                momentum("BTC", "flat", "medium", VALID),
                momentum("ETH", "up", "medium", VALID),
                momentum("SOL", "down", "medium", VALID)
            ),
            "0.50"
        );

        var result = service.getRiskFlags(symbols, FIAT);

        assertNoFlag(result, "flat_market");
        assertTrue(result.flags().isEmpty());
    }

    // 7. Con historia valida pero cambios cero,
    // Movers V1 puede devolver listas vacias.
    @Test
    void validFlatSeriesWithoutMoversShouldTriggerFlags() {
        var symbols = List.of("Q", "REZ");

        mockAnalysis(
            symbols,
            List.of(
                trend("Q", "flat", VALID),
                trend("REZ", "flat", VALID)
            ),
            List.of(
                momentum("Q", "flat", "medium", VALID),
                momentum("REZ", "flat", "medium", VALID)
            ),
            "0.50"
        );

        var result = service.getRiskFlags(symbols, FIAT);

        assertHasFlag(result, "flat_market");
        assertHasFlag(result, "no_clear_movers");
        assertNoFlag(result, "insufficient_history");
    }

    // 8. Confidence cero sin pares evaluables
    // no debe generar low_confidence_regime.
    @Test
    void noEvaluablePairsShouldNotTriggerLowConfidence() {
        var symbols = List.of("BTC");

        mockAnalysis(
            symbols,
            List.of(trend("BTC", "up", VALID)),
            List.of(momentum("BTC", "flat", "low", INVALID)),
            "0.00"
        );

        var result = service.getRiskFlags(symbols, FIAT);

        assertHasFlag(result, "insufficient_history");
        assertNoFlag(result, "low_confidence_regime");
        assertNoFlag(result, "weak_momentum");
    }

    // 9. Sesgo direccional debil con cobertura valida.
    @Test
    void weakDirectionalBiasShouldTriggerRegimeFlag() {
        var symbols = List.of("BTC");

        mockAnalysis(
            symbols,
            List.of(trend("BTC", "up", VALID)),
            List.of(momentum("BTC", "up", "medium", VALID)),
            "0.10"
        );

        var result = service.getRiskFlags(symbols, FIAT);

        assertEquals(1, result.flags().size());
        assertHasFlag(result, "low_confidence_regime");

        var flag = findFlag(
            result,
            "low_confidence_regime"
        );

        assertTrue(flag.detail().contains(
            "directional bias magnitude"
        ));

        assertTrue(flag.detail().contains(
            "does not measure data reliability"
        ));
    }

    // 10. Sin flags no significa ausencia de riesgo.
    @Test
    void noFlagsShouldUseConservativeSummary() {
        var symbols = List.of("BTC", "ETH");

        mockAnalysis(
            symbols,
            List.of(
                trend("BTC", "up", VALID),
                trend("ETH", "down", VALID)
            ),
            List.of(
                momentum("BTC", "up", "high", VALID),
                momentum("ETH", "down", "high", VALID)
            ),
            "0.50"
        );

        var result = service.getRiskFlags(symbols, FIAT);

        assertTrue(result.flags().isEmpty());

        assertEquals(
            "No diagnostic flags triggered for the evaluable assets.",
            result.summary()
        );
    }

    // ---------------------------------------
    // Fixtures y helpers
    // ---------------------------------------

    private void mockAnalysis(
        List<String> symbols,
        List<TrendService.TrendRow> trends,
        List<MomentumService.MomentumRow> momentum,
        String confidence
    ) {
        when(regimeService.getRegime(symbols, FIAT))
            .thenReturn(new RegimeService.RegimeResult(
                "neutral",
                BigDecimal.ZERO,
                new BigDecimal(confidence),
                "Test regime"
            ));

        when(trendService.getTrends(symbols, FIAT))
            .thenReturn(trends);

        when(momentumService.getMomentum(symbols, FIAT))
            .thenReturn(momentum);

        // Replica el contrato de Movers V1:
        // cambios positivos -> gainers
        // cambios negativos -> losers.
        var gainers = trends.stream()
            .filter(t -> VALID.equals(t.source()))
            .filter(t -> t.changePct().signum() > 0)
            .toList();

        var losers = trends.stream()
            .filter(t -> VALID.equals(t.source()))
            .filter(t -> t.changePct().signum() < 0)
            .toList();

        when(moverService.getMovers(symbols, FIAT, 3))
            .thenReturn(new MoverService.MoversResult(
                gainers,
                losers
            ));
    }

    private TrendService.TrendRow trend(
        String symbol,
        String direction,
        String source
    ) {
        BigDecimal change = switch (direction) {
            case "up" -> new BigDecimal("0.40");
            case "down" -> new BigDecimal("-0.40");
            default -> BigDecimal.ZERO;
        };

        return new TrendService.TrendRow(
            symbol,
            direction,
            change,
            change.abs(),
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

    private void assertHasFlag(
        RiskFlagService.RiskFlagsResult result,
        String code
    ) {
        assertTrue(
            result.flags().stream()
                .anyMatch(f -> code.equals(f.code())),
            "Expected flag: " + code
        );
    }

    private void assertNoFlag(
        RiskFlagService.RiskFlagsResult result,
        String code
    ) {
        assertFalse(
            result.flags().stream()
                .anyMatch(f -> code.equals(f.code())),
            "Unexpected flag: " + code
        );
    }

    private RiskFlagService.RiskFlag findFlag(
        RiskFlagService.RiskFlagsResult result,
        String code
    ) {
        return result.flags().stream()
            .filter(f -> code.equals(f.code()))
            .findFirst()
            .orElseThrow(() ->
                new AssertionError("Missing flag: " + code)
            );
    }
}
