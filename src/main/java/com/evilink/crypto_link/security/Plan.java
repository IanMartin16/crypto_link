package com.evilink.crypto_link.security;

public enum Plan {
    FREE(60, 1),
    PRO(600, 5),
    BUSINESS(6000, 20);

    public final int requestsPerMinute;
    public final int sseConnections;

    Plan(int rpm, int sseConn) {
        this.requestsPerMinute = rpm;
        this.sseConnections = sseConn;
    }
}
