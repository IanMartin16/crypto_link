package com.evilink.crypto_link.security;

import com.evilink.crypto_link.persistence.ApiKeyRepository;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Optional;

@Component
public class ApiKeyStore {

  public enum Plan {
     FREE(60, 1),
     BUSINESS(600, 5);

     public final int requestsPerMinute;
     public final int sseConnections;

     Plan(int rpm, int sseConn) {
        this.requestsPerMinute = rpm;
        this.sseConnections = sseConn;
        }
   }

    public enum Status { ACTIVE, REVOKED }

    private final ApiKeyRepository repo;

    public ApiKeyStore(ApiKeyRepository repo) {
        this.repo = repo;
    }

    public Plan getPlan(String apiKey) {
    return resolvePlan(apiKey).orElse(null);
    }

    public Optional<Plan> resolvePlan(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return Optional.empty();

        var rowOpt = repo.findByKey(apiKey.trim());
        if (rowOpt.isEmpty()) return Optional.empty();

        var row = rowOpt.get();

        // status
        Status st;
        try {
            st = Status.valueOf(row.status().toUpperCase());
        } catch (Exception e) {
            return Optional.empty();
        }
        if (st != Status.ACTIVE) return Optional.empty();

        // expiration
        OffsetDateTime exp = row.expiresAt();
        if (exp != null && exp.isBefore(OffsetDateTime.now())) return Optional.empty();

        // plan
        try {
            return Optional.of(Plan.valueOf(row.plan().toUpperCase()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
