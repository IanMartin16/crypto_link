package com.evilink.crypto_link.history;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class PriceHistoryRepository {

    private final JdbcTemplate jdbc;

    public PriceHistoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(String fiat, String symbol, BigDecimal price,
                     BigDecimal change24h, BigDecimal marketCap,
                     OffsetDateTime capturedAt) {
        jdbc.update(
            "INSERT INTO price_history " +
            "(fiat, symbol, price, change_24h, market_cap, captured_at) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
            fiat, symbol, price, change24h, marketCap, capturedAt
        );
    }

    /** Serie de un símbolo, más reciente primero (para los derivados, paso 2). */
    public List<PricePointRow> findSeries(String fiat, String symbol, int limit) {
        return jdbc.query(
            "SELECT price, change_24h, market_cap, captured_at " +
            "FROM price_history " +
            "WHERE fiat = ? AND symbol = ? " +
            "ORDER BY captured_at DESC " +
            "LIMIT ?",
            (rs, rowNum) -> new PricePointRow(
                rs.getBigDecimal("price"),
                rs.getBigDecimal("change_24h"),
                rs.getBigDecimal("market_cap"),
                rs.getObject("captured_at", OffsetDateTime.class)
            ),
            fiat, symbol, limit
        );
    }

    /** Cuántos puntos hay de un símbolo (para el fallback del paso 2). */
    public int countBySymbol(String fiat, String symbol) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM price_history WHERE fiat = ? AND symbol = ?",
            Integer.class, fiat, symbol
        );
        return n == null ? 0 : n;
    }

    public record PricePointRow(
        BigDecimal price,
        BigDecimal change24h,
        BigDecimal marketCap,
        OffsetDateTime capturedAt
    ) {}
}