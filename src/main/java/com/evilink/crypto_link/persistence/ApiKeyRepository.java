package com.evilink.crypto_link.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ApiKeyRepository {

    private final JdbcTemplate jdbc;

    public ApiKeyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ApiKeyRow> findByKey(String apiKey) {
        String sql = """
            select api_key, plan, status, expires_at
            from cryptolink_api_keys
            where api_key = ?
        """;

        List<ApiKeyRow> rows = jdbc.query(sql, (rs, rowNum) ->
                new ApiKeyRow(
                        rs.getString("api_key"),
                        rs.getString("plan"),
                        rs.getString("status"),
                        rs.getObject("expires_at", OffsetDateTime.class)
                ), apiKey);

        return rows.stream().findFirst();
    }

    public int insertKey(String apiKey, String plan, String status, OffsetDateTime expiresAt) {
    return jdbc.update("""
        insert into cryptolink_api_keys (api_key, plan, status, expires_at)
        values (?, ?, ?, ?)
        on conflict (api_key) do nothing
    """, apiKey, plan, status, expiresAt);
    }

    public int updateStatus(String apiKey, String status) {
        return jdbc.update("""
            update cryptolink_api_keys
            set status = ?
            where api_key = ?
        """, status, apiKey);
    }

    public int updatePlan(String apiKey, String plan) {
        return jdbc.update("""
            update cryptolink_api_keys
            set plan = ?
            where api_key = ?
        """, plan, apiKey);
    }

    public int updateExpiresAt(String apiKey, OffsetDateTime expiresAt) {
        return jdbc.update("""
            update cryptolink_api_keys
            set expires_at = ?
            where api_key = ?
        """, expiresAt, apiKey);
    }


    public record ApiKeyRow(String apiKey, String plan, String status, OffsetDateTime expiresAt) {}
}
