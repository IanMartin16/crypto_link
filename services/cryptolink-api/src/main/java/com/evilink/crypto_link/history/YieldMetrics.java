package com.evilink.crypto_link.history;

import java.math.BigDecimal;

public record YieldMetrics(String symbol, BigDecimal pxNow, BigDecimal px30,
                           BigDecimal pxP05, int niveles, boolean everUp, int has30d) {}