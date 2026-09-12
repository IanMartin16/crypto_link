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

    /** Retención: borra lo más viejo que N días. Rápido por el índice (captured_at). */
    public int deleteOlderThanDays(int days) {
        return jdbc.update(
            "DELETE FROM historical_prices WHERE captured_at < now() - (? || ' days')::interval",
            days
        );
    }
}