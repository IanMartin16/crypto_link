import type { SnapshotKPIs } from "./types";
import type { PriceRow } from "./types";


type TrendItem = {
  symbol: string;
  trend: "up" | "down" | "flat";
  score: number;
  reason?: string;
};

function pct(row?: PriceRow) {
  if (!row || typeof row.price !== "number" || typeof row.prevPrice !== "number" || row.prevPrice === 0) return null;
  return ((row.price - row.prevPrice) / row.prevPrice) * 100;
}

function fmtPct(v: number) {
  const sign = v > 0 ? "+" : "";
  return `${sign}${v.toFixed(2)}%`;
}

function moodLabel(score: number) {
  if (score >= 60) return "Strong Bullish";
  if (score >= 25) return "Bullish";
  if (score > -25) return "Neutral";
  if (score > -60) return "Bearish";
  return "Strong Bearish";
}

function topTrend(trends: TrendItem[]) {
  if (!trends?.length) return null;
  const sorted = [...trends].sort((a, b) => (b.score ?? 0) - (a.score ?? 0));
  return sorted[0] ?? null;
}

export function buildMarketInsight(args: {
  moodScore: number;
  rows: PriceRow[];
  trends: TrendItem[];
}) {
  const { moodScore, rows, trends } = args;

  const map = new Map(rows.map((r) => [r.symbol, r]));
  const btc = map.get("BTC");
  const eth = map.get("ETH");
  const btcPct = pct(btc);
  const ethPct = pct(eth);

  const top = topTrend(trends);

  const mood = moodLabel(moodScore);

  // Divergencia: mood positivo pero top trend negativo o viceversa
  const divergence =
    top &&
    ((moodScore >= 25 && top.trend === "down") ||
      (moodScore <= -25 && top.trend === "up"));

  // Línea 1: resumen de mood + drivers (BTC/ETH)
  let line1 = `Sentiment: ${mood}.`;
  const drivers: string[] = [];
  if (btcPct != null) drivers.push(`BTC ${fmtPct(btcPct)}`);
  if (ethPct != null) drivers.push(`ETH ${fmtPct(ethPct)}`);
  if (drivers.length) line1 += ` Drivers: ${drivers.join(" · ")}.`;

  // Línea 2: top mover + divergencia opcional
  let line2 = "";
  if (top) {
    const dir = top.trend === "up" ? "rising" : top.trend === "down" ? "falling" : "flat";
    line2 = `Top mover: ${top.symbol} (${dir}, score ${top.score.toFixed(2)}).`;
    if (top.reason) line2 += ` ${top.reason}`;
  }

  if (divergence) {
    line2 = `⚠ Divergence detected: mood is ${mood.toLowerCase()} but top movers show ${top?.trend.toUpperCase()} pressure.`;
  }

  // Fallback si no hay trends
  if (!line2) line2 = "Waiting for Social_link signals to enrich the narrative…";

  return { line1, line2, divergence: !!divergence };
}


type Mood = { score: number; confidence: number };

export type InsightV2 = {
  headline: string;
  summary: string;
  note?: string;
};

function clamp(n: number, min: number, max: number) {
  return Math.max(min, Math.min(max, n));
}

function pick(snapshot: SnapshotKPIs, key: string) {
  return snapshot.items.find((x) => x.key === key);
}

function toneWord(tone?: string) {
  switch (tone) {
    case "good": return "constructive";
    case "warn": return "mixed";
    case "bad": return "risk-off";
    default: return "neutral";
  }
}

function pressureRegime(p: number) {
  const a = Math.abs(p);
  if (a < 10) return "balanced";
  if (a < 30) return "tilted";
  return "strong";
}

function topTrends(trends: TrendItem[], n = 2) {
  return [...(trends || [])]
    .filter((t: any) => typeof t?.score === "number")
    .sort((a: any, b: any) => (b.score ?? 0) - (a.score ?? 0))
    .slice(0, n)
    .map((t: any) => t?.name ?? t?.title ?? "—");
}

export function buildInsightV2(input: {
  mood: Mood;
  snapshot: SnapshotKPIs;
  rows: PriceRow[];
  trends: TrendItem[];
}): InsightV2 {
  const moodScore = clamp(Number.isFinite(input.mood.score) ? input.mood.score : 0, -100, 100);
  const confidence = clamp(Number.isFinite(input.mood.confidence) ? input.mood.confidence : 0, 0, 1);

  const kUpDown = pick(input.snapshot, "upDown");
  const kDiv = pick(input.snapshot, "divergence");
  const kConf = pick(input.snapshot, "confidence");
  const kPress = pick(input.snapshot, "pressure");

  const pressure = Number.isFinite(kPress?.raw as number) ? (kPress!.raw as number) : moodScore;
  const regime = pressureRegime(pressure);
  const bias = moodLabel(moodScore);

  const div = Number.isFinite(kDiv?.raw as number) ? (kDiv!.raw as number) : 0;
  const divTone = toneWord(kDiv?.tone);

  const confPct = Math.round(confidence * 100);
  const confTone = toneWord(kConf?.tone);

  const themes = topTrends(input.trends, 2);
  const themeTxt = themes.length ? ` • themes: ${themes.join(", ")}` : "";

  const sign = pressure >= 0 ? "+" : "";
  const headline = `Market ${bias} • pressure ${sign}${Math.round(pressure)} (${regime})`;

  const upDown = kUpDown?.value ?? "-- / --";
  const divergence = kDiv?.value ?? "—";
  const summary =
    `Breadth ${upDown} • divergence ${divergence} (${divTone}) • confidence ${confPct}% (${confTone})${themeTxt}`;

  let note: string | undefined;

  if (div >= 45) {
    note = `Caution: signal/price alignment is ${div >= 70 ? "extremely" : "materially"} diverged — expect whipsaws.`;
  } else if (confidence < 0.45) {
    note = "Caution: low confidence — treat moves as noise until confirmation.";
  } else if (Math.abs(pressure) >= 30 && confidence >= 0.75) {
    note = "Bias is clean — momentum traders may lean with the pressure.";
  }

  return { headline, summary, note };
}

