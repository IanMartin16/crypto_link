"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { UI } from "@/lib/ui";
import { getSymbols } from "@/lib/symbolsStore";
import { Skeleton } from "@/components/Skeleton";
import Toast from "@/components/Toast";
import Sparkline from "@/components/Sparkline";
import { shortTs, shortTime } from "@/lib/format";
import Chip from "@/components/ui/Chip";
import type { Health } from "@/lib/health";
import { HEALTH_OK } from "@/lib/health"
import SymbolCell from "@/components/SymbolCell";
import { getSymbolName } from "@/lib/symbolMeta";


type TrendItem = {
  symbol: string;
  trend: "up" | "down" | "flat";
  score: number;
  reason: string;
};

type TrendsResponse = {
  ts: string;
  data: TrendItem[];
  items: TrendItem[];
  updatedAt: string;
};

function RefreshDot({ on }: { on: boolean }) {
    return (
      <span
        title={on ? "Actualizando" : "Idle"}
        style={{
          width: 8,
          height: 8,
          borderRadius: 999,
          display: "inline-block",
          background: on ? "rgba(255,159,67,0.95)" : "rgba(255,255,255,0.18)",
          boxShadow: on ? "0 0 14px rgba(255,159,67,0.35)" : "none",
          animation: on ? "clPulse 900ms ease-in-out infinite" : "none",
        }}
      />
    );
  }

async function fetchTrends(symbols: string[]) {
  const qs = encodeURIComponent(symbols.join(","));
  const res = await fetch(`/api/social/trends?symbols=${qs}`, { cache: "no-store" });
  if (!res.ok) throw new Error(`social_link HTTP ${res.status}`);
  return (await res.json()) as TrendsResponse;
}


function trendColor(trend: TrendItem["trend"]) {
  return trend === "up" ? UI.green : trend === "down" ? UI.red : "#bbb";
}

const alpha = (base: number, k: number) => base + k * 0.18;

  function heatRow(trend: TrendItem["trend"], k: number) {
    if (trend === "up") return `rgba(46,229,157,${alpha(0.06, k)})`;
    if (trend === "down") return `rgba(255,107,107,${alpha(0.06, k)})`;
    return `rgba(255,255,255,${0.03 + k * 0.10})`;
  }

  function heatBar(trend: TrendItem["trend"]) {
    if (trend === "up") return "rgba(46,229,157,0.10)";
    if (trend === "down") return "rgba(255,107,107,0.10)";
    return "rgba(255,255,255,0.08)";
  }

  function trendTint(trend: TrendItem["trend"]) {
    return trend === "up"
      ? "rgba(46,229,157,0.08)"
      : trend === "down"
      ? "rgba(255,107,107,0.08)"
      : "transparent";
  }

function TrendBadge({ trend }: { trend: TrendItem["trend"] }) {
  const c = trendColor(trend);
  const label = trend === "up" ? "UP" : trend === "down" ? "DOWN" : "FLAT";

  const bg =
    trend === "up"
      ? "rgba(46,229,157,0.10)"
      : trend === "down"
      ? "rgba(255,107,107,0.10)"
      : "rgba(255,255,255,0.06)";    

  return (
    <span
      style={{
        padding: "2px 10px",
        borderRadius: 999,
        border: `1px solid ${c === "#bbb" ? UI.border : c}`,
        color: c,
        background: bg,
        fontSize: 12,
        fontWeight: 900,
        letterSpacing: 0.2,
        whiteSpace: "nowrap",
      }}
    >
      {label}
    </span>
  );
}

type MarketStats = {
  total: number;
  up: number;
  down: number;
  flat: number;

  breadthUpPct: number;     // % de símbolos en UP
  breadthDownPct: number;   // % en DOWN
  mood: "BULLISH" | "BEARISH" | "NEUTRAL";

  avgScore: number;
  confidence: number;       // 0..100 (derivado de score y consenso)
  volatility: "LOW" | "MED" | "HIGH";

  topSymbol?: string;
};

function clamp(n: number, min: number, max: number) {
  return Math.max(min, Math.min(max, n));
}

function computeMarketStats(items: TrendItem[]): MarketStats {
  const total = items.length;
  const up = items.filter((t) => t.trend === "up").length;
  const down = items.filter((t) => t.trend === "down").length;
  const flat = items.filter((t) => t.trend === "flat").length;

  const breadthUpPct = total ? Math.round((up / total) * 100) : 0;
  const breadthDownPct = total ? Math.round((down / total) * 100) : 0;

  // Mood simple por mayoría
  const mood: MarketStats["mood"] =
    up > down ? "BULLISH" : down > up ? "BEARISH" : "NEUTRAL";

  // Score promedio (puede ser % o cualquier escala)
const scores = items.map((t) => (typeof t.score === "number" ? t.score : 0));
const avgScore = total ? scores.reduce((a, b) => a + b, 0) / total : 0;

// Consenso 0..1
const consensus = total ? Math.abs(up - down) / total : 0;

// ✅ Normalización robusta
// Si score está en % (ej 0.34, -0.07, 2.5), lo pasamos a 0..1 con cap
const SCORE_CAP = 5; // 5% = "fuerte"
const scoreAbs = Math.abs(avgScore);
const scoreNorm = clamp(scoreAbs / SCORE_CAP, 0, 1); // 0..1

// ✅ Confidence: mezcla magnitud + consenso
const confidence = Math.round(clamp(scoreNorm * 60 + consensus * 40, 0, 100));

  // Volatilidad “proxy” (sin precios): si hay mucha mezcla UP/DOWN/FLAT => más “volátil”
  // Si casi todos están del mismo lado => menos “volátil”
  const mix = 1 - consensus; // 0..1
  const flatPct = total ? flat / total : 0;

  let volatility: MarketStats["volatility"];
  if (flatPct > 0.75) volatility = "LOW";
  else volatility = mix > 0.66 ? "HIGH" : mix > 0.33 ? "MED" : "LOW";

  // Top symbol por score
  const top = [...items].sort((a, b) => (b.score ?? 0) - (a.score ?? 0))[0]?.symbol;

  return {
    total, up, down, flat,
    breadthUpPct,
    breadthDownPct,
    mood,
    avgScore: Number.isFinite(avgScore) ? avgScore : 0,
    confidence,
    volatility,
    topSymbol: top,
  };
}

export default function TrendsTable({
  onHealth,
  onItems,
}: {
  onHealth?: (h:{ ok: boolean; lastOkAt?: string; lastErr?: string }) => void;
  onItems?: (items: TrendItem[]) => void;
}) {
  const [items, setItems] = useState<TrendItem[]>([]);
  const [ts, setTs] = useState<string>("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [hover, setHover] = useState<string | null>(null);
  const [symbols, setSymbols] = useState<string[]>([]);
  const reqSeq = useRef(0);
  const [chipCount, setChipCount] = useState(0);
  const tsShort = shortTs(ts);
  const [auto, setAuto] = useState(true);
  const [filter, setFilter] = useState<"all" | "up" | "down">("all");
  const [toast, setToast] = useState<{ msg: string; tone?: "ok" | "warn" | "err" } | null>(null);
  const [hist, setHist] = useState<Record<string, number[]>>({});
  const [lastUpdated, setLastUpdated] = useState<string>("");

  useEffect(() => {
    const loadSymbols = () => {
      const s = getSymbols();
      setSymbols(s);
      setChipCount(s.length);
    };
    loadSymbols();
    window.addEventListener("cryptolink:symbols", loadSymbols as any);
    return () => window.removeEventListener("cryptolink:symbols", loadSymbols as any);
  }, []);

  async function load(kind: "initial" | "refresh" = "refresh") {
  const seq = ++reqSeq.current;

  try {
    const list = getSymbols();
    if (!list.length) return;

    const r = await fetchTrends(list);
    if (seq !== reqSeq.current) return;

    // ✅ normaliza timestamp (tu API devuelve updatedAt)
    const ts = (r as any).updatedAt ?? (r as any).ts ?? new Date().toISOString();

    // ✅ items vienen en r.items
    const raw = (r as any).items ?? [];

    // ✅ asegúrate que cada item traiga ts (para "Updated" y tabla)
    const nextItems: TrendItem[] = raw.map((t: any) => ({
      ...t,
      ts: t.ts ?? ts,
    }));

    // ✅ orden opcional (si tu UI quiere top primero)
    nextItems.sort((a, b) => (b.score ?? 0) - (a.score ?? 0));

    setTs(ts);
    setItems(nextItems);
    setLastUpdated(ts);

    const sorted = [...nextItems].sort((a, b) => (b.score ?? 0) - (a.score ?? 0));
    

     setHist((prev) => {
        const next = { ...prev };
        for (const it of sorted) {
          const arr = next[it.symbol] ? [...next[it.symbol]] : [];
          arr.push(it.score ?? 0);
          if (arr.length > 24) arr.splice(0, arr.length - 24);
          next[it.symbol] = arr;
        }
        return next;
      });

    queueMicrotask(() => {
      if (seq !== reqSeq.current) return;
      onItems?.(nextItems);
      onHealth?.({ ok: true, lastOkAt: ts });
    });
  } catch (e: any) {
    if (seq !== reqSeq.current) return;
    const msg = e?.message ?? "Error cargando trends";

    queueMicrotask(() => {
      if (seq !== reqSeq.current) return;
      onHealth?.({ ok: false, lastErr: msg });
    });
  }
}

useEffect(() => {
  load("initial");
  if (!auto) return;

  // ✅ polling 10s
  const id = setInterval(() => load("refresh"), 10000);

  // ✅ refresca si cambian símbolos
  const onSymbols = () => setTimeout(() => load("refresh"), 0);
  window.addEventListener("cryptolink:symbols" as any, onSymbols);

  return () => {
    clearInterval(id);
    window.removeEventListener("cryptolink:symbols" as any, onSymbols);
  };
  // eslint-disable-next-line react-hooks/exhaustive-deps
}, []);

  // 👇 intervalo de refresh
  useEffect(() => {
    load("initial");
    if (!auto) return;

    const id = setInterval(() => load("refresh"), 10000);
    return () => clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auto, symbols.length]);

  useEffect(() => {
      setItems((prev) => {
      items.forEach((t) => {
        const list = historyRef.current[t.symbol] || [];
        const next = [...list.slice(-19), t.score ?? 0]; // max 20 puntos
        historyRef.current[t.symbol] = next;
      });
      return items;
    });
  }, [items]);

  function CopyIcon({ show }: { show: boolean }) {
    return (
      <span
        style={{
          display: "inline-flex",
          alignItems: "center",
          opacity: show ? 0.9 : 0,
          transform: show ? "translateX(0)" : "translateX(-2px)",
          transition: "opacity 120ms ease, transform 120ms ease",
        }}
        aria-hidden
      >
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
          <path
            d="M8 8V6.8C8 5.805 8.805 5 9.8 5H18.2C19.195 5 20 5.805 20 6.8V15.2C20 16.195 19.195 17 18.2 17H17"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
          />
          <path
            d="M6.8 8H15.2C16.195 8 17 8.805 17 9.8V18.2C17 19.195 16.195 20 15.2 20H6.8C5.805 20 5 19.195 5 18.2V9.8C5 8.805 5.805 8 6.8 8Z"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
          />
        </svg>
      </span>
    );
  }

  function slope(values: number[]) {
    if (!values || values.length < 2) return 0;
    const a = values[0];
    const b = values[values.length - 1];
    if (a === 0) return 0;
    return (b - a) / Math.abs(a); // porcentaje relativo
  }

  function momentumLabel(score?: number, s?: number) {
    const sc = typeof score === "number" ? score : 0;
    const sl = typeof s === "number" ? s : 0;

    // tuning suave (ajustable)
    if (sc >= 6 && sl > 0.01) return "STRONG";
    if (sc >= 3 && sl > 0.005) return "GROWING";
    if (sc <= -6 && sl < -0.01) return "DUMPING";
    if (sc <= -3 && sl < -0.005) return "WEAK";
    return "NEUTRAL";
  }

  function momentumTone(m: string) {
    if (m === "STRONG") return { c: UI.green, bg: "rgba(46,229,157,0.10)", b: "rgba(46,229,157,0.25)" };
    if (m === "GROWING") return { c: "rgba(46,229,157,0.85)", bg: "rgba(46,229,157,0.06)", b: "rgba(46,229,157,0.18)" };
    if (m === "DUMPING") return { c: UI.red, bg: "rgba(255,107,107,0.10)", b: "rgba(255,107,107,0.25)" };
    if (m === "WEAK") return { c: "rgba(255,107,107,0.85)", bg: "rgba(255,107,107,0.06)", b: "rgba(255,107,107,0.18)" };
    return { c: "rgba(255,255,255,0.75)", bg: "rgba(255,255,255,0.05)", b: UI.border };
  }

  const showSkeleton = loading && items.length === 0;

  function Chip({ children }: { children: React.ReactNode }) {
    return (
      <span
        style={{
          padding: "3px 8px",
          borderRadius: 999,
          border: `1px solid ${UI.border}`,
          background: "rgba(255,255,255,0.03)",
          fontSize: 11,
          fontWeight: 800,
          opacity: 0.9,
          whiteSpace: "nowrap",
          maxWidth: "100%",
          overflow: "hidden",
          textOverflow: "ellipsis",
        }}
      >
        {children}
      </span>
    );
  }

  function ChipBtn({
    active,
    tone,
    onClick,
    children,
    title,
  }: {
    active: boolean;
    tone?: "up" | "down" | "neutral";
    onClick: () => void;
    children: React.ReactNode;
    title?: string;
  }) {
    const c =
      tone === "up"
        ? UI.green
        : tone === "down"
        ? UI.red
        : "rgba(255,255,255,0.85)";

    const border = active ? "rgba(255,159,67,0.45)" : UI.border;
    const bg = active ? "rgba(255,159,67,0.10)" : "rgba(255,255,255,0.03)";
    const glow = active ? "0 0 16px rgba(255,159,67,0.14)" : "none";

    return (
      <button
        onClick={onClick}
        title={title}
        style={{
          all: "unset",
          cursor: "pointer",
          padding: "4px 10px",
          borderRadius: 999,
          border: `1px solid ${border}`,
          background: bg,
          boxShadow: glow,
          fontSize: 12,
          fontWeight: 950,
          color: active ? UI.orangeSoft : c,
          whiteSpace: "nowrap",
        }}
      >
        {children}
      </button>
    );
  }

  function shortTs(v: string) {
    if (!v) return "-";
    const d = new Date(v);
    if (isNaN(d.getTime())) return v.slice(0, 10);
    return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
  }

  function shortTime(v?: string) {
    if (!v) return "—";
    const d = new Date(v);
    if (isNaN(d.getTime())) return "—";
    return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
  }

  const maxScore = Math.max(0.0001, ...items.map((x) => Math.abs(x.score ?? 0)));
  const intensity = (score: number) => Math.min(1, Math.abs(score ?? 0) / maxScore);
  const historyRef = useRef<Record<string, number[]>>({});

  const viewItems = filter === "all" ? items : items.filter((t) => t.trend === filter);

  const stats = useMemo(() => computeMarketStats(items), [items]);

  const moodBase = items.length ? items : [];

  const upCount = moodBase.filter((t) => t.trend === "up").length;
  const downCount = moodBase.filter((t) => t.trend === "down").length;
  const flatCount = moodBase.filter((t) => t.trend === "flat").length;
  const total = moodBase.length || 1;

  const upPct = Math.round((upCount / total) * 100);
  const downPct = Math.round((downCount / total) * 100);

  // fuerza neta (simple): promedio signed score  
  const signed = moodBase.map((t) => {
    const s = typeof t.score === "number" ? t.score : 0;
    return t.trend === "up" ? s : t.trend === "down" ? -s : 0;
  });
  const strength = signed.reduce((a, b) => a + b, 0) / (moodBase.length || 1);

   // confidence 0..100 (basado en magnitud)
  const confidence = Math.min(100, Math.round(Math.abs(strength) * 12)); // tuning ligero

  const mood =
    strength > 0.15 ? "BULLISH" : strength < -0.15 ? "BEARISH" : "NEUTRAL";

  const moodTone =
    mood === "BULLISH" ? UI.green : mood === "BEARISH" ? UI.red : "rgba(255,255,255,0.75)";

  const moodBg =
   mood === "BULLISH"
      ? "rgba(46,229,157,0.10)"
      : mood === "BEARISH"
      ? "rgba(255,107,107,0.10)"
      : "rgba(255,255,255,0.05)";


  useEffect(() => {
    const f = window.localStorage.getItem("cl_trends_filter") as any;
    if (f === "all" || f === "up" || f === "down") setFilter(f);

    const a = window.localStorage.getItem("cl_trends_auto");
    if (a === "0" || a === "1") setAuto(a === "1");
  }, []);

  useEffect(() => {
    window.localStorage.setItem("cl_trends_filter", filter);
  }, [filter]);

  useEffect(() => {
    window.localStorage.setItem("cl_trends_auto", auto ? "1" : "0");
  }, [auto]);

  function clamp01(x: number) {
    return Math.max(0, Math.min(1, x));
  }

  // score -> intensidad 0..1 (tuning: 0..10 aprox)
  function intensityFromScore(score?: number) {
    if (typeof score !== "number") return 0;
    return clamp01(Math.abs(score) / 10);
  }

  return (
    <section
      style={{
        marginTop: UI.gap,
        padding: UI.padLg,
        border: `1px solid ${UI.border}`,
        borderRadius: UI.radiusLg,
        background:
          stats.mood === "BULLISH"
            ? "linear-gradient(180deg, rgba(46,229,157,0.06), rgba(255,255,255,0.02))"
            : stats.mood === "BEARISH"
            ? "linear-gradient(180deg, rgba(255,107,107,0.06), rgba(255,255,255,0.02))"
            : UI.panel,
        position: "relative",
        overflow: "hidden",
        minHeight: 300,
      }}
    >
      {refreshing && (
        <div
          style={{
            position: "absolute",
            top: 0,
            left: 0,
            right: 0,
            height: 2,
            background:
              "linear-gradient(90deg, transparent, rgba(255,159,67,0.95), transparent)",
            transform: "translateX(-60%)",
            animation: "clSweep 650ms ease-out infinite",
            opacity: 0.9,
            pointerEvents: "none",
          }}
        />
      )}

      {/* HEADER pro: Market Snapshot */}
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          gap: 14,
          alignItems: "flex-start",
        }}
      >
        {/* LEFT */}
          <div style={{ minWidth: 0 }}>
            <h2 style={{ margin: 0 }}>
              Trends <span style={{ color: UI.orange }}>(Social_link)</span>
            </h2>
            <div
              style={{
                marginTop: 10,
                padding: "8px 12px",
                borderRadius: 12,
                fontSize: 12,
                opacity: 0.85,
                border: `1px solid ${UI.border}`,
                background: "rgba(255,255,255,0.02)",
              }}
            >
              {stats.mood === "BULLISH" && (
                <>📈 Social sentiment aligned to upside momentum.</>
              )}

              {stats.mood === "BEARISH" && (
                <>📉 Social sentiment showing downside pressure.</>
              )}

              {stats.mood === "NEUTRAL" && (
                <>⚖️ Market sentiment balanced. No clear dominance.</>
              )}
            </div>

            <div style={{ marginTop: 6, fontSize: 12, opacity: 0.78 }}>
              Movers · refresh: <span style={{ color: UI.orangeSoft, fontWeight: 900 }}>10s</span>{" "}
              · filter: <span style={{ color: UI.orangeSoft, fontWeight: 900 }}>{filter.toUpperCase()}</span>
            </div>
          </div>

          {/* RIGHT: Snapshot */}
            <div style={{ minWidth: 0, display: "grid", gap: 8, justifyItems: "end" }}>
              {/* Snapshot card */}
              <div
                style={{
                  border: `1px solid ${UI.border}`,
                  background: "rgba(255,255,255,0.03)",
                  borderRadius: 14,
                  padding: "10px 12px",
                  boxShadow: "0 10px 30px rgba(0,0,0,0.25)",
                  maxWidth: 520,
                }}
              >
                <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "center", justifyContent: "flex-end" }}>
                  {/* Mood */}
                  <span style={{ fontSize: 12, opacity: 0.75 }}>Mood:</span>
                  <span
                    style={{
                      padding: "3px 10px",
                      borderRadius: 999,
                      fontSize: 12,
                      fontWeight: 950,
                      border: `1px solid ${stats.mood === "BULLISH" ? UI.green : stats.mood === "BEARISH" ? UI.red : UI.border}`,
                      background:
                        stats.mood === "BULLISH"
                          ? "rgba(46,229,157,0.10)"
                          : stats.mood === "BEARISH"
                          ? "rgba(255,107,107,0.10)"
                          : "rgba(255,255,255,0.06)",
                      color: stats.mood === "BULLISH" ? UI.green : stats.mood === "BEARISH" ? UI.red : UI.orangeSoft,
                      whiteSpace: "nowrap",
                    }}
                  >
                    {stats.mood}
                  </span>

                  {/* Up/Down */}
                  <span style={{ fontSize: 12, opacity: 0.75 }}>Up/Down:</span>
                  <span style={{ fontSize: 12, fontWeight: 950, color: UI.orangeSoft, whiteSpace: "nowrap" }}>
                    {stats.breadthUpPct}% / {stats.breadthDownPct}%
                  </span>

                  {/* Confidence */}
                  <span style={{ fontSize: 12, opacity: 0.75 }}>Confidence:</span>
                  <span style={{ fontSize: 12, fontWeight: 950, color: UI.orangeSoft }}>
                    {stats.confidence}%
                  </span>

                  {/* Vol */}
                  <span style={{ fontSize: 12, opacity: 0.75 }}>Vol:</span>
                  <span style={{ fontSize: 12, fontWeight: 950, color: UI.orangeSoft }}>
                    {stats.volatility}
                  </span>
                </div>
              </div>

              {/* Controls row */}
              <div
                style={{
                  display: "flex",
                  gap: 8,
                  flexWrap: "wrap",
                  justifyContent: "flex-end",
                  alignItems: "center",
                  maxWidth: 520,
                }}
              >
                {/* Auto */}
                <Chip>
                  Auto:{" "}
                  <button
                    onClick={() => setAuto((v) => !v)}
                    style={{
                      all: "unset",
                      cursor: "pointer",
                      fontWeight: 950,
                      color: auto ? UI.green : UI.red,
                      marginLeft: 6,
                    }}
                  >
                    {auto ? "ON" : "OFF"}
                  </button>
                </Chip>

                {/* Updated + ts */}
                <Chip>
                  Updated: <span style={{ color: UI.orangeSoft }}>{shortTime(ts)}</span>
                </Chip>
                <Chip>
                  ts: <span style={{ color: UI.orangeSoft }}>{tsShort}</span>
                </Chip>

                {/* Filter buttons */}
                <Chip>
                <ChipBtn active={filter === "all"} tone="neutral" onClick={() => setFilter("all")} title="Solo ALL">
                  ALL
                </ChipBtn>
                </Chip>
                <Chip>
                <ChipBtn active={filter === "up"} tone="up" onClick={() => setFilter("up")} title="Solo UP">
                  UP
                </ChipBtn>
              </Chip>
              <Chip>
                <ChipBtn active={filter === "down"} tone="down" onClick={() => setFilter("down")} title="Solo DOWN">
                  DOWN
                </ChipBtn>
              </Chip>
            </div>
          </div>
        </div>

      {error && (
        <div style={{ marginTop: 10, color: UI.red, fontSize: 12 }}>
          ⚠ Social_link no disponible ({error}). Mostrando último dato válido.
        </div>
      )}

      {showSkeleton ? (
        <div style={{ marginTop: 12 }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr style={{ textAlign: "left", borderBottom: `1px solid ${UI.border}` }}>
                <th style={{ padding: "10px 8px", fontSize: 12, opacity: 0.75, fontWeight: 800 }}>#</th>
                <th style={{ padding: "10px 8px", fontSize: 12, opacity: 0.75, fontWeight: 800 }}>Symbol</th>
                <th style={{ padding: "10px 8px", fontSize: 12, opacity: 0.75, fontWeight: 800 }}>Trend</th>
                <th style={{ padding: "10px 8px", fontSize: 12, opacity: 0.75, fontWeight: 800 }}>Score</th>
                <th style={{ padding: "10px 8px", fontSize: 12, opacity: 0.75, fontWeight: 800 }}>Reason</th>
              </tr>
            </thead>
            <tbody>
              {[...Array(5)].map((_, i) => (
                <tr key={i} style={{ borderBottom: `1px solid ${UI.borderSoft}` }}>
                  <td style={{ padding: "12px 8px" }}>
                    <Skeleton w={60} h={12} />
                  </td>
                  <td style={{ padding: "12px 8px" }}>
                    <Skeleton w={80} h={12} r={999} />
                  </td>
                  <td style={{ padding: "12px 8px", textAlign: "right" }}>
                    <Skeleton w={50} h={12} />
                  </td>
                  <td style={{ padding: "12px 8px" }}>
                    <Skeleton w={160} h={12} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div style={{ marginTop: 12, overflowX: "auto", maxHeight: 420}}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr style={{ textAlign: "left", borderBottom: `1px solid ${UI.border}`, position: "sticky", top: 0, background: UI.panel, zIndex: 1, }}>
                <th style={{ padding: "10px 8px", fontSize: 12, opacity: 0.75, fontWeight: 800 }}>#</th>
                <th style={{ padding: "10px 8px", fontSize: 12, opacity: 0.75, fontWeight: 800 }}>Symbol</th>
                <th style={{ padding: "10px 8px", fontSize: 12, opacity: 0.75, fontWeight: 800 }}>Trend</th>
                <th style={{ padding: "10px 8px", fontSize: 12, opacity: 0.75, fontWeight: 800 }}>Score</th>
                <th style={{ padding: "10px 8px", fontSize: 12, opacity: 0.75, fontWeight: 800 }}>Reason</th>
                <th style={{ padding: "10px 8px", fontSize: 12, opacity: 0.75, fontWeight: 800 }}>Momentum</th>
              </tr>
            </thead>
            <tbody>
              {viewItems.map((t, idx) => {
                const rank = idx + 1;
                const c = trendColor(t.trend);
                const isHover = hover === t.symbol;
                const a = intensityFromScore(t.score);
                const isTop = rank <= 3;
                const fullName = getSymbolName(t.symbol);

                const rankStyle = isTop
                  ? {
                      background: "rgba(255,159,67,0.12)",
                      border: `1px solid rgba(255,159,67,0.22)`,
                      boxShadow: "0 0 18px rgba(255,159,67,0.10)",
                      color: UI.orangeSoft,
                    }
                  : {
                      background: "rgba(255,255,255,0.03)",
                      border: `1px solid ${UI.border}`,
                      color: "rgba(255,255,255,0.70)",
                    };
                
                const bg =
                  t.trend === "up"
                    ? `rgba(46,229,157,${0.04 + a * 0.12})`
                    : t.trend === "down"
                    ? `rgba(255,107,107,${0.04 + a * 0.12})`
                    : `rgba(255,255,255,${0.02 + a * 0.06})`;

                     // bar lateral (heat bar)
                const bar =
                  t.trend === "up"
                    ? `rgba(46,229,157,${0.25 + a * 0.55})`
                    : t.trend === "down"
                    ? `rgba(255,107,107,${0.25 + a * 0.55})`
                    : `rgba(255,255,255,${0.18 + a * 0.25})`;   
                    
                const h = hist[t.symbol] ?? [];
                const sl = slope(h);
                const mom = momentumLabel(t.score, sl);
                const mt = momentumTone(mom);

                return (
                  <tr
                    key={t.symbol}
                    onMouseEnter={() => setHover(t.symbol)}
                    onMouseLeave={() => setHover(null)}
                    style={{
                      borderBottom: `1px solid ${UI.borderSoft}`,
                      background: isHover ? "rgba(255,159,67,0.06)" : bg,
                      transition: "background 120ms ease",
                    }}
                  >
                    {/* # */}
                    <td style={{ padding: "12px 8px", position: "relative" }}>
                      <span
                        style={{
                          display: "inline-grid",
                          placeItems: "center",
                          width: 28,
                          height: 22,
                          borderRadius: 999,
                          fontWeight: 950,
                          fontSize: 12,
                          ...rankStyle,
                        }}
                        title={isTop ? "Top mover" : "Rank"}
                      >
                        {rank}
                      </span>
                    </td>

                    <td style={{ padding: "12px 8px", fontWeight: 950 }}>
                      <button
                        onClick={async () => {
                          try {
                            await navigator.clipboard.writeText(t.symbol);
                            setToast({ msg: `Copiado: ${t.symbol}`, tone: "ok" });
                          } catch {
                            setToast({ msg: "No pude copiar (permiso del navegador).", tone: "err" });
                          }
                        }}
                        style={{
                          all: "unset",
                          cursor: "pointer",
                          display: "inline-flex",
                          alignItems: "center",
                          gap: 8,
                          color: "#e6edf3",
                        }}
                          title="Copiar símbolo"
                      >
                        <span
                          style={{
                            textShadow: isHover ? "0 0 10px rgba(255,159,67,0.18)" : "none",
                          }}
                        >
                          <SymbolCell symbol={t.symbol} />
                        </span>
                        <div className="flex flex-col min-w-0">
                          {fullName ? (
                            <div className="hidden sm:block text-[13px] text-white/45 truncate">
                              {fullName}
                          </div>
                          ) : null}
                        </div>
                        <span style={{ color: UI.orangeSoft }}>
                          <CopyIcon show={isHover} />
                        </span>
                      </button>
                    </td>

                      {/* Trend (Badge + sparkline ) */}
                      <td style={{ padding: "12px 8px" }}>
                        <div style={{ display: "inline-flex", alignItems: "center", gap: 10 }}>
                          <TrendBadge trend={t.trend} />
                          {/* aquí deja tu sparkline si ya está integrado */}
                        </div>
                      </td>

                      <td style={{ marginLeft: 4 }}>
                        <Sparkline
                          values={hist[t.symbol] ?? []}
                          w={82}
                          h={20}
                          stroke={c}
                          fill={
                            t.trend === "up"
                              ? "rgba(46,229,157,0.10)"
                              : t.trend === "down"
                              ? "rgba(255,107,107,0.10)"
                              : "rgba(255,255,255,0.06)"
                            }
                          />
                        {typeof t.score === "number" ? t.score.toFixed(2) : "—"}
                      </td>
                      {/* ✅ Reason con ellipsis */}
                    <td style={{ padding: "12px 8px", fontSize: 12, opacity: 0.85, maxWidth: 260 }}>
                      <span
                        style={{
                          display: "block",
                          color: c,
                          overflow: "hidden",
                          whiteSpace: "nowrap",
                          textOverflow: "ellipsis",
                        }}
                        title={t.reason || ""}
                      >
                        {t.reason }
                        <Sparkline
                          values={hist[t.symbol] ?? []}
                          w={88}
                          h={22}
                          stroke={c}
                          fill={t.trend === "up"
                            ? "rgba(46,229,157,0.10)"
                            : t.trend === "down"
                            ? "rgba(255,107,107,0.10)"
                            : "rgba(255,255,255,0.08)"
                          }
                        />
                      </span>
                    </td>
                      <td style={{ padding: "12px 8px" }}>
                        <span
                          style={{
                            display: "inline-flex",
                            alignItems: "center",
                            gap: 8,
                            padding: "3px 10px",
                            borderRadius: 999,
                            border: `1px solid ${mt.b}`,
                            background: mt.bg,
                            color: mt.c,
                            fontSize: 12,
                            fontWeight: 950,
                            whiteSpace: "nowrap",
                          }}
                          title={`slope ${(sl*100).toFixed(2)}%`}
                        >
                          {mom}
                          <span style={{ opacity: 0.75, fontWeight: 900, color: mt.c }}>
                            {sl === 0 ? "·" : sl > 0 ? "↗" : "↘"}
                          </span>
                        </span>
                      </td>
                    </tr>
                  );
              })}
              {items.length === 0 && !error && (
                <tr>
                  <td colSpan={4} style={{ padding: "12px 8px", opacity: 0.75 }}>
                    Sin datos todavía…
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
      <Toast toast={toast} onClear={() => setToast(null)} />
    </section>
  );
}
