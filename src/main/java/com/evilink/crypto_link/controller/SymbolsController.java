package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.service.SymbolService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;

@ApiResponses({
  @ApiResponse(responseCode="200", description="OK",
    headers = {
      @Header(name="X-Plan"),
      @Header(name="X-RateLimit-Limit"),
      @Header(name="X-RateLimit-Remaining"),
      @Header(name="X-RateLimit-Reset"),
      @Header(name="X-RateLimit-Used")
    }
  ),
  @ApiResponse(responseCode="401", description="Invalid or missing x-api-key", content=@Content),
  @ApiResponse(responseCode="429", description="Rate limit exceeded",
    headers = { @Header(name="Retry-After") },
    content=@Content
  )
})


@RestController
public class SymbolsController {

  private final SymbolService symbols;

  public SymbolsController(SymbolService symbols) {
    this.symbols = symbols;
  }

  @Operation(security = {})
  @GetMapping("/v1/symbols")
  public Map<String, Object> list() {

    var set = symbols.listActive();
    var list = set.stream().sorted().toList();

    return Map.of("ok", true, "symbols", list);
  }
}
