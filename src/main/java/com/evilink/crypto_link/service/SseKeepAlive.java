package com.evilink.crypto_link.service;

import com.evilink.crypto_link.sse.PriceBroadcaster;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SseKeepAlive {

    private final PriceBroadcaster broadcaster;

    public SseKeepAlive(PriceBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Scheduled(fixedRate = 15000)
    public void ping() {
        broadcaster.broadcastPing();
    }
}
