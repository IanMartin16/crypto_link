export type PriceRow = {
  updatedAt?: string | undefined;
  symbol: string;
  fiat?: string;
  price?: number;
  prevPrice?: number;
  pct?: number;         // si ya lo calculas en UI, opcional
  ok?: boolean;
  cache?: string;
  ts?: string;
  source?: string;
};

export type TrendItem = {
  symbol: string;
  trend: "up" | "down" | "flat";
  score: number;
  reason?: string;
};

export type MoodInsight = {
  line1: string;
  line2: string;
  divergence?: boolean;
};

export type MoodResult = {
  score: number;          // -100..100
  confidence: number;     // 0..1
};

export type BadgeTone = "good" | "warn" | "bad" | "neutral";

export type SnapshotKPIKey = "upDown" | "divergence" | "confidence" | "pressure";

export type SnapshotKPI = {
  key: SnapshotKPIKey;
  label: string;
  value: string;        // lo que se imprime (ej: "62% / 38%", "High", "+18")
  sub?: string;         // opcional (ej: "12 assets", "vs last tick")
  tone: BadgeTone;      // para color/estilo consistente
  raw?: number;         // número para ordenar/debug si lo necesitas
};

export type SnapshotKPIs = {
  updatedAt: number;    // Date.now()
  items: SnapshotKPI[]; // los 4 KPIs en orden
};