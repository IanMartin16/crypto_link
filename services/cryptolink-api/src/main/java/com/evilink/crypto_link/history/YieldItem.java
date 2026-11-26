package com.evilink.crypto_link.history;

import java.math.BigDecimal;

public record YieldItem(String symbol, BigDecimal nav, Double apy30d, String status) {}