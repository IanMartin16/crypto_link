"use client";

import { useEffect, useRef, useState } from "react";
import { UI } from "@/lib/ui";
import { getSymbols } from "@/lib/symbolsStore";
import { Skeleton } from "@/components/Skeleton";
import Toast from "@/components/Toast";
import Sparkline from "@/components/Sparkline";
import { shortTs, shortTime } from "@/lib/format";
import Chip from "@/components/ui/Chip";
import type { Health } from "@/lib/health";


type TrendItem = {
  symbol: string;
  trend: "up" | "down" | "flat";
  score: number;
  reason: string;
};

type TrendsResponse = {
  ts: string;
  data: TrendItem[];
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
  if (!res.ok) throw new Error(`Social_link HTTP ${res.status}`);
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

export default function TrendsTable({
  onHealth,
}: {
  onHealth?: (h: Health) => void;
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
      setError(null);
      if (kind === "initial") setLoading(true);
      else setRefreshing(true);

      const list = symbols?.length ? symbols : getSymbols();
      if (!list?.length) return;

      const r = await fetchTrends(list);
      if (seq !== reqSeq.current) return;

      setTs(r.ts ?? "");
      const sorted = [...(r.data ?? [])].sort((a, b) => (b.score ?? 0) - (a.score ?? 0));
      setItems(sorted);
      setLastUpdated(new Date().toISOString());

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
        onHealth?.({ ok: true, lastOkAt: new Date().toISOString() });
      });
    } catch (e: any) {
      if (seq !== reqSeq.current) return;
      const msg = e?.message ?? "Error trends";
      setError(msg);

      queueMicrotask(() => {
        if (seq !== reqSeq.current) return;
        onHealth?.({ ok: false, lastErr: msg });
      });
    } finally {
      if (seq !== reqSeq.current) return;
      setLoading(false);
      setRefreshing(false);
    }
  }

  // 👇 si quieres evitar doble intervalo, deja SOLO este (auto controla)
  useEffect(() => {
    load("initial");
    if (!auto) return;

    const id = setInterval(() => load("refresh"), 10000);
    return () => clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auto]);

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

  return (
    <section
      style={{
        marginTop: UI.gap,
        padding: UI.padLg,
        border: `1px solid ${UI.border}`,
        borderRadius: UI.radiusLg,
        background: UI.panel,
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

      <div style={{ display: "flex", justifyContent: "space-between", gap: 12, alignItems: "flex-start" }}>
        <div style={{ minWidth: 0 }}>
          <h2 style={{ margin: 0, fontSize: 18, fontWeight: 900, letterSpacing: 0.3}}>
            Trends <span style={{ color: UI.orange }}>(Social_link)</span>
          </h2>
          <div style={{ marginTop: 6, fontSize: 11, opacity: 0.65 }}>
            Movers · refresh: 10s · filter: {filter.toUpperCase()}
          </div>
        </div>

        <div
          style={{
            display: "flex",
            gap: 8,
            flexWrap: "wrap",
            justifyContent: "flex-end",
            maxWidth: "55%",
            minWidth: 0,
          }}
        >
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

          <Chip>
            Symbols: <span style={{ color: UI.orangeSoft }}>{chipCount || 0}</span>
          </Chip>
          <Chip>
            ts: <span style={{ color: UI.orangeSoft }}>{tsShort}</span>
          </Chip>
          <Chip>
            Updated:{" "}
            <span style={{ color: UI.orangeSoft }}>
              {lastUpdated
                ? new Date(lastUpdated).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" })
                : "--"}
            </span>
          </Chip>
          <Chip>
            <span style={{ display: "inline-flex", alignItems: "center", gap: 8 }}>
              <RefreshDot on={auto && refreshing } />
              {refreshing || loading ? "updating" : "idle"}
            </span>
          </Chip>

          {/* filtros (botones) */}
          <ChipBtn active={filter === "all"} tone="neutral" onClick={() => setFilter("all")} title="Mostrar todos">
            ALL
          </ChipBtn>
          <ChipBtn active={filter === "up"} tone="up" onClick={() => setFilter("up")} title="Solo UP">
            UP
          </ChipBtn>
          <ChipBtn active={filter === "down"} tone="down" onClick={() => setFilter("down")} title="Solo DOWN">
            DOWN
          </ChipBtn>
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
                <th style={{ padding: "10px 8px", fontSize: 12, opacity: 0.75, fontWeight: 800 }}>Symbol</th>
                <th style={{ padding: "10px 8px", fontSize: 12, opacity: 0.75, fontWeight: 800 }}>Trend</th>
                <th style={{ padding: "10px 8px", fontSize: 12, opacity: 0.75, fontWeight: 800 }}>Score</th>
                <th style={{ padding: "10px 8px", fontSize: 12, opacity: 0.75, fontWeight: 800 }}>Reason</th>
              </tr>
            </thead>
            <tbody>
              {viewItems.map((t, idx) => {
                const isHover = hover === t.symbol;
                const zebra = idx % 2 === 0 ? "rgba(255,255,255,0.02)" : "transparent";
                const bg = isHover ? "rgba(255,159,67,0.06)" : trendTint(t.trend);
                
                const k = intensity(t.score ?? 0);
                const c = trendColor(t.trend); 
                const heat =
                  t.trend === "up"
                    ? `rgba(46,229,157,${0.08 + k * 0.18})`
                    : t.trend === "down"
                    ? `rgba(255,107,107,${0.08 + k * 0.18})`
                    : `rgba(255,255,255,${0.04 + k * 0.10})`;

                return (
                  <tr
                    key={t.symbol}
                    onMouseEnter={() => setHover(t.symbol)}
                    onMouseLeave={() => setHover(null)}
                    style={{
                      borderBottom: `1px solid ${UI.borderSoft}`,
                      background: bg,
                      transition: "background 140ms ease, transform 140ms ease",
                      transform: isHover ? "translateY(-1px)" : "translateY(0)",
                    }}
                  >
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
                          style={{ all: "unset", cursor: "pointer", fontWeight: 950 }}
                          title="Copiar símbolo"
                        >
                          <span
                            style={{
                              textShadow: isHover ? "0 0 10px rgba(255,159,67,0.18)" : "none",
                            }}
                          >
                          {t.symbol}
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
                        <span style={{ color: UI.orangeSoft}}>
                          <CopyIcon show={isHover} />
                        </span>
                      </button>
                    </td>

                    {/* ✅ Trend: solo badge (sin duplicar texto UP/DOWN) */}
                    <td style={{ padding: "12px 8px" }}>
                      <TrendBadge trend={t.trend} />
                      <div style={{ marginLeft: 2, opacity: 0.95 }}>
                        <Sparkline
                          values={hist[t.symbol] ?? []}
                          w={88}
                          h={22}
                          stroke={c}
                          fill={t.trend === "up" ? "rgba(46,229,157,0.10)" : t.trend === "down" ? "rgba(255,107,107,0.10)" : "rgba(255,255,255,0.08)"}
                        />
                      </div>
                    </td>

                    {/* ✅ Score a 2 decimales, alineado derecha */}
                    <td style={{ padding: "12px 8px" }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                      <div style={{ fontWeight: 900, minWidth: 56 }}>
                        {typeof t.score === "number" ? t.score.toFixed(2) : "—"}
                        { t.score }
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
                      </div>
                    </div>
                  </td>
                    {/* ✅ Reason con ellipsis */}
                    <td style={{ padding: "12px 8px", fontSize: 12, opacity: 0.85, maxWidth: 260 }}>
                      <span
                        style={{
                          display: "block",
                          overflow: "hidden",
                          whiteSpace: "nowrap",
                          textOverflow: "ellipsis",
                        }}
                        title={t.reason || ""}
                      >
                        {t.reason || "—"}
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
