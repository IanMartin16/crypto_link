import type { PriceRow, TrendItem, SnapshotKPIs, SnapshotKPI } from "./types";

type SnapshotInput = {
  rows: PriceRow[];
  trends: TrendItem[];
  moodScore: number;   // -100..100
  confidence: number;  // 0..1
};

function clamp(n: number, min: number, max: number) {
  return Math.max(min, Math.min(max, n));
}

function safe(n: number | undefined, fallback = NaN) {
  return Number.isFinite(n as number) ? (n as number) : fallback;
}

function momentumSign(m: any): number {
  if (m === "up" || m === "UP") return 1;
  if (m === "down" || m === "DOWN") return -1;
  if (m === "flat" || m === "FLAT") return 0;

  const num = typeof m === "number" ? m : Number(m);
  if (Number.isFinite(num)) return num > 0 ? 1 : num < 0 ? -1 : 0;

  return 0;
}

function divergenceLevel(div: number): { label: string; tone: SnapshotKPI["tone"] } {
  if (div < 20) return { label: "Low", tone: "good" };
  if (div < 45) return { label: "Med", tone: "neutral" };
  if (div < 70) return { label: "High", tone: "warn" };
  return { label: "Extreme", tone: "bad" };
}

export function computeSnapshotKPIs(input: SnapshotInput): SnapshotKPIs {
  const moodScore = clamp(Number.isFinite(input.moodScore) ? input.moodScore : 0, -100, 100);
  const confidence = clamp(Number.isFinite(input.confidence) ? input.confidence : 0, 0, 1);

  // -------------------------
  // 1) UP/DOWN %
  // -------------------------
let up = 0;
let down = 0;
let flat = 0;
let valid = 0;

const FLAT_EPS = 0.0002; // 0.02%

for (const r of input.rows || []) {
  const price = safe(r.price);
  const prev = safe(r.prevPrice);

  if (!Number.isFinite(price) || !Number.isFinite(prev) || prev === 0) continue;

  // ✅ prioriza pct si ya lo traes calculado
  let delta: number | null = null;

  if (typeof r.pct === "number" && Number.isFinite(r.pct)) {
    delta = r.pct / 100; // pct viene en %
  } else if (Number.isFinite(price) && Number.isFinite(prev) && prev !== 0) {
    delta = (price - prev) / prev;
  }

  if (delta == null) continue;
  valid++;

  if (delta > 0) up++;
  else if (delta < 0) down++;
  else flat++;
  }

const upPct = valid ? (up / valid) * 100 : 0;
const downPct = valid ? (down / valid) * 100 : 0;
const flatPct = valid ? (flat / valid) * 100 : 0;

const upDownDiff = upPct - downPct;

const diffThresh = valid >= 12 ? 10 : 20;
const upDownTone: SnapshotKPI["tone"] =
  upDownDiff >= diffThresh ? "good" : upDownDiff <= -diffThresh ? "bad" : "neutral";

// breadth -100..100
const breadth = valid ? ((up - down) / valid) * 100 : 0;

const warming = valid < 3;

  // -------------------------
  // 2) Divergence (breadth vs mood)
  // -------------------------
  const divergence = clamp(Math.abs(breadth - moodScore) / 2, 0, 100);
  const divMeta = divergenceLevel(divergence);

  // -------------------------
  // 3) Confidence badge
  // -------------------------
  const confTone: SnapshotKPI["tone"] =
    confidence >= 0.75 ? "good" :
    confidence >= 0.55 ? "neutral" :
    confidence >= 0.35 ? "warn" : "bad";

  // -------------------------
  // 4) Market Pressure
  // -------------------------
  let trendSum = 0;
  let trendCount = 0;

  for (const t of input.trends || []) {
    const score = (t as any)?.score;
    const scoreN = typeof score === "number" ? clamp(score, 0, 100) : NaN;
    if (!Number.isFinite(scoreN)) continue;

    const sign = momentumSign((t as any)?.momentum);
    if (sign === 0) continue;

    trendSum += sign * scoreN;
    trendCount++;
  }

  const trendNet = trendCount ? clamp(trendSum / trendCount, -100, 100) : 0;

  const base = 0.7 * moodScore + 0.3 * trendNet;
  const pressure = clamp(base * (0.5 + 0.5 * confidence), -100, 100);

  const pressureAbs = Math.abs(pressure);
  const pressureTone: SnapshotKPI["tone"] =
    pressureAbs < 10 ? "neutral" :
    pressureAbs < 30 ? "warn" :
    pressure >= 0 ? "good" : "bad";

  const items: SnapshotKPI[] = [
    {
      key: "pressure",
      label: "Market Pressure",
      value: `${Math.round(pressure)}`,
      sub: trendCount
        ? `mood ${Math.round(moodScore)} • trend ${Math.round(trendNet)}`
        : `mood ${Math.round(moodScore)}`,
      tone: pressureTone,
      raw: pressure,
    },
    {
      key: "upDown",
      label: "Up/Down",
      value: valid ? `${Math.round(upPct)}% / ${Math.round(downPct)}%` : "warming…",
      sub: valid ? `flat ${Math.round(flatPct)}% • ${valid} assets` : `waiting for prevPrice • ${input.rows.length} assets`,
      tone: valid ? upDownTone : "neutral",
      raw: upDownDiff,
    },
    {
      key: "divergence",
      label: "Divergence",
      value: divMeta.label,
      sub: valid
        ? `breadth ${Math.round(breadth)} vs mood ${Math.round(moodScore)}`
        : `mood ${Math.round(moodScore)}`,
      tone: valid ? divMeta.tone : "neutral",
      raw: divergence,
    },
    {
      key: "confidence",
      label: "Confidence",
      value: `${Math.round(confidence * 100)}%`,
      sub: "signal strength",
      tone: confTone,
      raw: confidence,
    },
  ];

  return {
    updatedAt: Date.now(),
    items,
  };
}