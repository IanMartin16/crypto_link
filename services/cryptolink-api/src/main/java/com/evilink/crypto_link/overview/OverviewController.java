package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.overview.OverviewService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/v1")
public class OverviewController {

    private final OverviewService overviewService;

    public OverviewController(OverviewService overviewService) {
        this.overviewService = overviewService;
    }

    // Paginado sobre el universo rankeado por market cap. Sin page -> primera página.
    // page: 1-based. limit: acotado server-side a [1,100].
    @GetMapping("/overview")
    public ResponseEntity<?> getOverview(
        @RequestParam(defaultValue = "USD") String fiat,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "100") int limit
    ) {
        String f = fiat.toUpperCase();
        var pageResult = overviewService.getOverviewPage(f, page, limit);

        Map<String, Object> body = new HashMap<>();
        body.put("ok", true);
        body.put("fiat", f);
        body.put("ts", OffsetDateTime.now().toString());
        body.put("source", "historical-prices-db");
        // edad real del dato (snapshot horario). null si aún no hay data.
        body.put("updatedAt", pageResult.updatedAt() == null ? null : pageResult.updatedAt().toString());
        body.put("page", pageResult.page());
        body.put("pageSize", pageResult.pageSize());
        body.put("total", pageResult.total());
        body.put("totalPages", pageResult.totalPages());
        body.put("count", pageResult.items().size());
        body.put("items", pageResult.items());

        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
            .body(body);
    }
}
