package com.evilink.crypto_link.security;

public enum Plan {
    FREE(30, 1),
    PRO(120, 1),
    BUSINESS(6000, 20);

    public final int requestsPerMinute;
    public final int sseConnections;

    Plan(int rpm, int sseConn) {
        this.requestsPerMinute = rpm;
        this.sseConnections = sseConn;
    }
}
