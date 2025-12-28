package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.service.SymbolService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;


@RestController
public class SymbolsController {

  private final SymbolService symbols;

  public SymbolsController(SymbolService symbols) {
    this.symbols = symbols;
  }

  @GetMapping("/v1/symbols")
  public Map<String, Object> list() {

    var set = symbols.listActive();
    var list = set.stream().sorted().toList();

    return Map.of("ok", true, "symbols", list);
  }
}
