package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.service.FiatService;

import io.swagger.v3.oas.annotations.Operation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class FiatsController {

  private final FiatService fiats;

  public FiatsController(FiatService fiats) {
    this.fiats = fiats;
  }

  @Operation(security = {})
  @GetMapping("/v1/fiats")
  public Map<String, Object> list() {
    var list = fiats.listActiveSet().stream().sorted().toList();
    return Map.of("ok", true, "fiats", list);
  }
}
