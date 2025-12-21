package com.evilink.crypto_link.security;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SseTokenService {

    public static class Ctx {
        public final String apiKey;
        public final ApiKeyStore.Plan plan;
        public final long expiresAtEpochSec;

        public Ctx(String apiKey, ApiKeyStore.Plan plan, long expiresAtEpochSec) {
            this.apiKey = apiKey;
            this.plan = plan;
            this.expiresAtEpochSec = expiresAtEpochSec;
        }
    }

    private final SecureRandom rnd = new SecureRandom();
    private final Map<String, Ctx> tokens = new ConcurrentHashMap<>();

    // token válido por 60s (puedes subirlo a 120s si quieres)
    private final long ttlSeconds = 60;

    public String mint(String apiKey, ApiKeyStore.Plan plan) {
        byte[] b = new byte[32];
        rnd.nextBytes(b);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(b);

        long exp = Instant.now().getEpochSecond() + ttlSeconds;
        tokens.put(token, new Ctx(apiKey, plan, exp));
        return token;
    }

    /** Solo para SSE: valida token y regresa contexto si sigue vivo */
    public Ctx resolve(String token) {
        if (token == null || token.isBlank()) return null;

        Ctx ctx = tokens.get(token);
        if (ctx == null) return null;

        long now = Instant.now().getEpochSecond();
        if (ctx.expiresAtEpochSec < now) {
            tokens.remove(token);
            return null;
        }
        return ctx;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }
}
