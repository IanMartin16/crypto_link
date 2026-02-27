type TrendItem = { symbol: string; trend?: "up" | "down" | "flat"; score: number };

const FLAT_EPS_PCT = 0.05; // 0.05% (ajústalo: 0.1 si lo quieres menos nervioso)

function inferTrend(scorePct: number): "up" | "down" | "flat" {
  if (!Number.isFinite(scorePct)) return "flat";
  if (scorePct > FLAT_EPS_PCT) return "up";
  if (scorePct < -FLAT_EPS_PCT) return "down";
  return "flat";
}

export function normalizeTrends(items: TrendItem[]) {
  const list = (items ?? []).map((t) => {
    const score = Number.isFinite(t.score) ? t.score : 0;
    const trend = t.trend ?? inferTrend(score);
    return { ...t, score, trend };
  });

  const sorted = [...list].sort((a, b) => (b.score ?? 0) - (a.score ?? 0));
  return sorted.map((t, i) => ({ ...t, rank: i + 1 }));
}