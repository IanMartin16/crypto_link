package com.evilink.crypto_link.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Repository
public class ApiKeyRepository {

    private static final Set<String> ALLOWED_PLANS =
        Set.of("BUSINESS", "PRO");

    private static final Set<String> ALLOWED_STATUSES =
        Set.of("ACTIVE", "DISABLED");

    private final JdbcTemplate jdbc;

    public ApiKeyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ApiKeyRow> findByKey(String apiKey) {
        String sql = """
            select api_key, plan, status, expires_at
            from cryptolink_api_keys
            where api_key = ?
            limit 1
        """;

        List<ApiKeyRow> rows = jdbc.query(
            sql,
            (rs, rowNum) -> new ApiKeyRow(
                rs.getString("api_key"),
                rs.getString("plan"),
                rs.getString("status"),
                rs.getObject("expires_at", OffsetDateTime.class)
            ),
            apiKey
        );

        return rows.stream().findFirst();
    }

    public int insertKey(
        String apiKey,
        String plan,
        String status,
        OffsetDateTime expiresAt
    ) {
        String normalizedPlan = normalizePlan(plan);
        String normalizedStatus = normalizeStatus(status);

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException(
                "CryptoLink API key cannot be blank"
            );
        }

        return jdbc.update("""
            insert into cryptolink_api_keys(
              api_key,
              plan,
              status,
              expires_at,
              created_at
            )
            values (?, ?, ?, ?, now())
            on conflict (api_key) do nothing
            """,
            apiKey,
            normalizedPlan,
            normalizedStatus,
            expiresAt
        );
    }

    public int updateStatus(String apiKey, String status) {
        String normalizedStatus = normalizeStatus(status);

        return jdbc.update("""
            update cryptolink_api_keys
            set status = ?
            where api_key = ?
            """,
            normalizedStatus,
            apiKey
        );
    }

    public int updatePlan(String apiKey, String plan) {
        String normalizedPlan = normalizePlan(plan);

        return jdbc.update("""
            update cryptolink_api_keys
            set plan = ?
            where api_key = ?
            """,
            normalizedPlan,
            apiKey
        );
    }

    public int updateExpiresAt(
        String apiKey,
        OffsetDateTime expiresAt
    ) {
        return jdbc.update("""
            update cryptolink_api_keys
            set expires_at = ?
            where api_key = ?
            """,
            expiresAt,
            apiKey
        );
    }

    public Optional<ApiKeyRow> findActiveByKey(String apiKey) {
        return jdbc.query("""
            select api_key, plan, status, expires_at
            from cryptolink_api_keys
            where api_key = ?
              and status = 'ACTIVE'
              and (
                expires_at is null
                or expires_at > now()
              )
            limit 1
            """,
            rs -> rs.next()
                ? Optional.of(new ApiKeyRow(
                    rs.getString("api_key"),
                    rs.getString("plan"),
                    rs.getString("status"),
                    rs.getObject(
                        "expires_at",
                        OffsetDateTime.class
                    )
                ))
                : Optional.empty(),
            apiKey
        );
    }

    public int deactivate(String apiKey) {
        return jdbc.update("""
            update cryptolink_api_keys
            set status = 'DISABLED'
            where api_key = ?
              and status <> 'DISABLED'
            """,
            apiKey
        );
    }

    private static String normalizePlan(String plan) {
        if (plan == null || plan.isBlank()) {
            throw new IllegalArgumentException(
                "CryptoLink plan cannot be blank"
            );
        }

        String normalized =
            plan.trim().toUpperCase(Locale.ROOT);

        if (!ALLOWED_PLANS.contains(normalized)) {
            throw new IllegalArgumentException(
                "Unsupported CryptoLink plan: " + normalized
            );
        }

        return normalized;
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException(
                "CryptoLink API key status cannot be blank"
            );
        }

        String normalized =
            status.trim().toUpperCase(Locale.ROOT);

        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException(
                "Unsupported API key status: " + normalized
            );
        }

        return normalized;
    }

    public record ApiKeyRow(
        String apiKey,
        String plan,
        String status,
        OffsetDateTime expiresAt
    ) {}
}