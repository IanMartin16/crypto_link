package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.service.FiatService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class FiatsController {

  private final FiatService fiats;

  public FiatsController(FiatService fiats) {
    this.fiats = fiats;
  }

  @GetMapping("/v1/fiats")
  public Map<String, Object> list() {
    return Map.of("ok", true, "fiats", fiats.listActiveSet());
  }
}
