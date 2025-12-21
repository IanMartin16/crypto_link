package com.evilink.crypto_link.metrics;

import com.evilink.crypto_link.sse.PriceBroadcaster;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class SseMetrics {

    public SseMetrics(MeterRegistry registry, PriceBroadcaster broadcaster) {
        Gauge.builder("cryptolink_sse_connections_active", broadcaster, PriceBroadcaster::activeConnections)
                .register(registry);
    }
}
