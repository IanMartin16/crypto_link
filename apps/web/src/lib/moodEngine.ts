import type { PriceRow } from "./types";


export type TrendItem = {
  symbol: string;
  trend: "up" | "down" | "flat";
  score: number;
};

function pct(row?: PriceRow) {
  if (!row || typeof row.price !== "number" || typeof row.prevPrice !== "number" || row.prevPrice === 0) return 0;
  return ((row.price - row.prevPrice) / row.prevPrice) * 100;
}

export function computeMood(rows: PriceRow[], trends: TrendItem[]) {
  const m = new Map(rows.map((r) => [r.symbol, r]));
  const btc = m.get("BTC");
  const eth = m.get("ETH");

  const btcW = 0.6;
  const ethW = 0.4;

  const priceSignal = btcW * pct(btc) + ethW * pct(eth);

  const trendSignal =
    trends?.length
      ? trends.reduce((acc, t) => {
          const dir = t.trend === "up" ? 1 : t.trend === "down" ? -1 : 0;
          return acc + dir * (t.score ?? 0);
        }, 0) / trends.length
      : 0;

  const scoreRaw = priceSignal * 10 + trendSignal * 8;
  const score = Math.max(-100, Math.min(100, scoreRaw));

  // ✅ coverage (datos presentes)
  const totalRows = rows.length || 1;
  const priceSignals = rows.filter((r) => typeof r.pct === "number" && Number.isFinite(r.pct)).length;
  const priceCoverage = priceSignals / totalRows;

  const totalTrends = trends.length || 1;
  const trendSignals = trends.filter((t) => typeof t.score === "number" && Number.isFinite(t.score)).length;
  const trendCoverage = trendSignals / totalTrends;

  // ✅ confidence estable y “viva”
  const confidence = Math.max(
    0.25,
    Math.min(0.95, 0.15 + 0.65 * priceCoverage + 0.20 * trendCoverage)
  );

  return { score, confidence };
}
