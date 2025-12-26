package com.evilink.crypto_link.validation;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class MarketValidator {

    // MVP whitelist (ampliamos luego)
    private static final Set<String> ALLOWED_FIAT = Set.of("USD", "MXN");

    private static final Set<String> ALLOWED_SYMBOLS = Set.of(
            "BTC","ETH","SOL","XRP","ADA","DOGE"
            // luego metemos SOL, XRP, ADA, etc
    );

    public String normalizeFiat(String fiat) {
        String f = (fiat == null ? "" : fiat.trim().toUpperCase());
        if (!ALLOWED_FIAT.contains(f)) {
            throw new IllegalArgumentException("Unsupported fiat. Allowed: " + ALLOWED_FIAT);
        }
        return f;
    }

    public List<String> normalizeSymbols(String symbolsCsv) {
        if (symbolsCsv == null || symbolsCsv.isBlank()) {
            throw new IllegalArgumentException("symbols is required");
        }

        List<String> list = Arrays.stream(symbolsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(String::toUpperCase)
                .distinct()
                .collect(Collectors.toList());

        List<String> invalid = list.stream()
                .filter(s -> !ALLOWED_SYMBOLS.contains(s))
                .toList();

        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException("Unsupported symbols: " + invalid + ". Allowed: " + ALLOWED_SYMBOLS);
        }

        return list;
    }

    public List<String> normalizeSymbols(List<String> symbols) {
        String csv = String.join(",", symbols == null ? List.of() : symbols);
        return normalizeSymbols(csv);
    }

    public Set<String> allowedSymbols() {
        return ALLOWED_SYMBOLS;
    }

    public Set<String> allowedFiats() {
        return ALLOWED_FIAT;
    }

}
