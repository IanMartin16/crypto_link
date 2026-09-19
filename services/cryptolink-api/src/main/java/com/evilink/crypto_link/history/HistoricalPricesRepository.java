package com.evilink.crypto_link.history;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class HistoricalPricesRepository {

    private final JdbcTemplate jdbc;

    public HistoricalPricesRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Un punto a insertar en historical_prices. */
    public record Row(
        String fiat,
        String symbol,
        BigDecimal price,
        BigDecimal marketCap,
        BigDecimal volume24h,
        BigDecimal change24h,
        OffsetDateTime sourceUpdatedAt,   // last_updated_at de CoinGecko (puede ser null)
        OffsetDateTime capturedAt
    ) {}

    /** Inserta en lote (todos los símbolos de un ciclo del job en una operación). */
    public void saveAll(List<Row> rows) {
        if (rows == null || rows.isEmpty()) return;

        String sql =
            "INSERT INTO historical_prices " +
            "(fiat, symbol, price, market_cap, volume_24h, change_24h, source_updated_at, captured_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Row r = rows.get(i);
                ps.setString(1, r.fiat());
                ps.setString(2, r.symbol());
                ps.setBigDecimal(3, r.price());
                ps.setBigDecimal(4, r.marketCap());
                ps.setBigDecimal(5, r.volume24h());
                ps.setBigDecimal(6, r.change24h());
                ps.setTimestamp(7, r.sourceUpdatedAt() == null ? null
                        : Timestamp.from(r.sourceUpdatedAt().toInstant()));
                ps.setTimestamp(8, Timestamp.from(r.capturedAt().toInstant()));
            }
            @Override
            public int getBatchSize() { return rows.size(); }
        });
    }

    public List<BigDecimal> findPriceSeries(String fiat, String symbol, int limit) {
        return jdbc.query(
            "SELECT price FROM historical_prices " +
            "WHERE fiat = ? AND symbol = ? " +
            "ORDER BY captured_at DESC LIMIT ?",
            (rs, rowNum) -> rs.getBigDecimal("price"),
            fiat, symbol, limit
        );
    }

    /** Todos los símbolos distintos que hay en historical_prices para un fiat. */
    public List<String> findSymbols(String fiat) {
        return jdbc.queryForList(
            "SELECT DISTINCT symbol FROM historical_prices WHERE fiat = ? ORDER BY symbol",
            String.class, fiat
        );
    }

    public List<OverviewRow> findLatestPerSymbol(String fiat) {
        return jdbc.query(
            "SELECT DISTINCT ON (symbol) symbol, price, change_24h, volume_24h, " +
            "       market_cap, captured_at " +
            "FROM historical_prices WHERE fiat = ? " +
            "ORDER BY symbol, captured_at DESC",
            (rs, n) -> new OverviewRow(
                rs.getString("symbol"),
                rs.getBigDecimal("price"),
                rs.getBigDecimal("change_24h"),
                rs.getBigDecimal("volume_24h"),
                rs.getBigDecimal("market_cap"),
                rs.getObject("captured_at", java.time.OffsetDateTime.class)
            ),
            fiat
        );
    }

    /** Serie de precios de un símbolo con su timestamp, ASC (viejo→nuevo),
     *  desde hace `sinceHours`. Para calcular 7d y el sparkline. */
    public List<PricePoint> findSeriesSince(String fiat, String symbol, int sinceHours) {
        return jdbc.query(
            "SELECT price, captured_at FROM historical_prices " +
            "WHERE fiat = ? AND symbol = ? AND captured_at >= now() - (? || ' hours')::interval " +
            "ORDER BY captured_at ASC",
            (rs, n) -> new PricePoint(
                rs.getBigDecimal("price"),
                rs.getObject("captured_at", java.time.OffsetDateTime.class)
            ),
            fiat, symbol, sinceHours
        );
    }

    public record OverviewRow(
        String symbol, BigDecimal price, BigDecimal change24h,
        BigDecimal volume24h, BigDecimal marketCap,
        java.time.OffsetDateTime capturedAt
    ) {}

    public record PricePoint(BigDecimal price, java.time.OffsetDateTime capturedAt) {}

    /** Retención: borra lo más viejo que N días. Rápido por el índice (captured_at). */
    public int deleteOlderThanDays(int days) {
        return jdbc.update(
            "DELETE FROM historical_prices WHERE captured_at < now() - (? || ' days')::interval",
            days
        );
    }
}