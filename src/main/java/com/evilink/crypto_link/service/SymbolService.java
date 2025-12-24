package com.evilink.crypto_link.service;

import com.evilink.crypto_link.persistence.SymbolRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class SymbolService {

  private final SymbolRepository repo;

  // cache simple (60s)
  private final long ttlMs = 60_000;
  private final AtomicReference<Cache> cache = new AtomicReference<>(new Cache(0, List.of()));

  public SymbolService(SymbolRepository repo) {
    this.repo = repo;
  }

  public List<String> listActive() {
    long now = System.currentTimeMillis();
    Cache c = cache.get();
    if (now - c.tsMs < ttlMs && !c.symbols.isEmpty()) return c.symbols;

    List<String> fresh = repo.listActiveSymbols();
    cache.set(new Cache(now, fresh));
    return fresh;
  }

  public Map<String, String> resolveIds(List<String> symbolsUpper) {
    return repo.resolveIds(symbolsUpper);
  }

  private record Cache(long tsMs, List<String> symbols) {}
}
