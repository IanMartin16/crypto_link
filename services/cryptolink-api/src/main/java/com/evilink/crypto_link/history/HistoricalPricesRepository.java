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

    public List<DailyRollup> aggregateDay(String fiat, java.time.LocalDate day) {
        return jdbc.query(
            "SELECT symbol, " +
            "  (array_agg(price ORDER BY captured_at ASC))[1]  AS open, " +
            "  MAX(price) AS high, " +
            "  MIN(price) AS low, " +
            "  (array_agg(price ORDER BY captured_at DESC))[1] AS close, " +
            "  AVG(price) AS avg_price, " +
            "  AVG(volume_24h) AS volume_avg, " +
            "  (array_agg(market_cap ORDER BY captured_at DESC))[1] AS market_cap, " +
            "  COUNT(*) AS points " +
            "FROM historical_prices " +
            "WHERE fiat = ? AND captured_at >= ?::date AND captured_at < (?::date + interval '1 day') " +
            "GROUP BY symbol",
            (rs, n) -> new DailyRollup(
                rs.getString("symbol"),
                rs.getBigDecimal("open"),
                rs.getBigDecimal("high"),
                rs.getBigDecimal("low"),
                rs.getBigDecimal("close"),
                rs.getBigDecimal("avg_price"),
                rs.getBigDecimal("volume_avg"),
                rs.getBigDecimal("market_cap"),
                rs.getInt("points")
            ),
            fiat, day.toString(), day.toString()
        );
    }

    /** Retención: borra lo más viejo que N días. Rápido por el índice (captured_at). */
    public int deleteOlderThanDays(int days) {
        return jdbc.update(
            "DELETE FROM historical_prices WHERE captured_at < now() - (? || ' days')::interval",
            days
        );
    }

    /** Inserta los rollups del día (idempotente: ON CONFLICT actualiza). */
    public void saveDaily(String fiat, java.time.LocalDate day, List<DailyRollup> rows) {
        String sql =
            "INSERT INTO price_daily " +
            "(fiat, symbol, day, open, high, low, close, avg_price, volume_avg, market_cap, points) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?) " +
            "ON CONFLICT (fiat, symbol, day) DO UPDATE SET " +
            "  open=EXCLUDED.open, high=EXCLUDED.high, low=EXCLUDED.low, close=EXCLUDED.close, " +
            "  avg_price=EXCLUDED.avg_price, volume_avg=EXCLUDED.volume_avg, " +
            "  market_cap=EXCLUDED.market_cap, points=EXCLUDED.points";

        jdbc.batchUpdate(sql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
            @Override public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                DailyRollup r = rows.get(i);
                ps.setString(1, fiat);
                ps.setString(2, r.symbol());
                ps.setObject(3, day);
                ps.setBigDecimal(4, r.open());
                ps.setBigDecimal(5, r.high());
                ps.setBigDecimal(6, r.low());
                ps.setBigDecimal(7, r.close());
                ps.setBigDecimal(8, r.avgPrice());
                ps.setBigDecimal(9, r.volumeAvg());
                ps.setBigDecimal(10, r.marketCap());
                ps.setInt(11, r.points());
            }
            @Override public int getBatchSize() { return rows.size(); }
        });
    }

    public record DailyRollup(
        String symbol, java.math.BigDecimal open, java.math.BigDecimal high,
        java.math.BigDecimal low, java.math.BigDecimal close, java.math.BigDecimal avgPrice,
        java.math.BigDecimal volumeAvg, java.math.BigDecimal marketCap, int points
    ) {}
}