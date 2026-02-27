import type { PriceRow } from "./types";

export type PricePoint = { t: number; price: number };

const MAX_POINTS = 600;

// history por símbolo
const history: Record<string, PricePoint[]> = {};

// evita meter el mismo punto repetido si llega mismo precio en el mismo tick
const lastSeen: Record<string, { t: number; price: number }> = {};

export function pushPricesToHistory(rows: PriceRow[], now = Date.now()) {
  for (const r of rows) {
    const sym = r.symbol;
    const price = r.price;

    if (!sym || typeof price !== "number" || !Number.isFinite(price)) continue;

    const prev = lastSeen[sym];
    if (prev && prev.price === price && Math.abs(now - prev.t) < 1500) {
      continue; // mismo valor casi mismo tiempo -> skip
    }

    lastSeen[sym] = { t: now, price };

    const arr = history[sym] ?? (history[sym] = []);
    arr.push({ t: now, price });

    // ring buffer manual
    if (arr.length > MAX_POINTS) arr.splice(0, arr.length - MAX_POINTS);
  }
}

export function getHistory(symbol: string): PricePoint[] {
  return history[symbol] ?? [];
}

export function getAvailableSymbols(): string[] {
  return Object.keys(history).sort();
}