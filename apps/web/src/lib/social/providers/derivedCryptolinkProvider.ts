import type { TrendsProvider, TrendsRequest, TrendsResponse } from "./trendsProviders";
import type { TrendItem, PriceRow } from "@/lib/types";
import { fetchPricesBatch } from "@/lib/cryptoLink"; // ✅ tu helper
import { normalizeTrends } from "@/lib/trendEngine"; // ajusta nombre si es distinto

function pricesToTrendSeed(rows: PriceRow[]): TrendItem[] {
  const FLAT_PCT = 0.03; // 0.03% umbral flat

  return rows.map((r) => {
    const pct = typeof r.pct === "number" ? r.pct : 0;

    let trend: "up" | "down" | "flat" = "flat";
    let score = 0;

    if (Math.abs(pct) >= FLAT_PCT) {
      trend = pct > 0 ? "up" : "down";

      // escala % → score (0..100)
      // 0.10% → 2
      // 1% → 20
      // 5% → 100 (cap)
      score = Math.min(100, Math.round(Math.abs(pct) * 20));
    }

    return {
      symbol: r.symbol,
      trend,
      score,
      reason:
        trend === "flat"
          ? "Price stable"
          : trend === "up"
          ? `Up ${pct.toFixed(2)}%`
          : `Down ${pct.toFixed(2)}%`,
      ts: r.ts ?? new Date().toISOString(),
    };
  });
}

export const derivedCryptolinkProvider: TrendsProvider = {
  name: "derived",

  async getTrends(req: TrendsRequest): Promise<TrendsResponse> {
    const domain = req.domain ?? "crypto";
    const fiat = req.fiat ?? "USD";

    // ✅ usa el mismo flujo que PricesPanel (helper)
    const res = await fetchPricesBatch(req.symbols, fiat, /* apiKey? */ undefined as any);

    // OJO: si fetchPricesBatch requiere apiKey siempre, dime y lo adaptamos (modo showcase)
    const rows: PriceRow[] = (res?.data ?? []).map((x: any) => {
      // si tu fetchPricesBatch ya devuelve PriceRow “limpio”, puedes quitar este map
      const d = x?.data;
      return {
        symbol: x.symbol,
        fiat: res.fiat ?? fiat,
        price: d?.price,
        prevPrice: d?.prevPrice,
        pct: d?.pct,
        ok: x.ok,
        cache: x.cache,
        ts: d?.ts,
        source: d?.source,
      } as PriceRow;
    });

    const seed = pricesToTrendSeed(rows);

    // ✅ normaliza (tu engine)
    const items = normalizeTrends(seed) as TrendItem[];

    return {
      ok: true,
      domain,
      source: "derived",
      updatedAt: new Date().toISOString(),
      items,
      note: "Derived from CryptoLink market data (no external social providers yet).",
    };
  },
};