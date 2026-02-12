"use client";

import { useEffect, useRef, useState } from "react";
import { UI } from "@/lib/ui";
import { getSymbols } from "@/lib/symbolsStore";
import { Skeleton } from "@/components/Skeleton";

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

async function fetchTrends(symbols: string[]) {
  const qs = encodeURIComponent(symbols.join(","));
  const res = await fetch(`/api/social/trends?symbols=${qs}`, { cache: "no-store" });
  if (!res.ok) throw new Error(`Social_link HTTP ${res.status}`);
  return (await res.json()) as TrendsResponse;
}

function trendColor(trend: TrendItem["trend"]) {
  return trend === "up" ? UI.green : trend === "down" ? UI.red : "#bbb";
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
  onHealth?: (h: { ok: boolean; lastOkAt?: string; lastErr?: string }) => void;
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


  // load symbols + listen updates
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
      if (!list?.length) {
        // no symbols -> don't wipe UI
        return;
      }

      const r = await fetchTrends(list);

      if (seq !== reqSeq.current) return;

      setTs(r.ts ?? "");
      const sorted = [...(r.data ?? [])].sort((a, b) => (b.score ?? 0) - (a.score ?? 0));
      setItems(sorted);

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

  useEffect(() => {
    load("initial");
    const id = setInterval(() => load("refresh"), 10000);
    return () => clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const showSkeleton = loading && items.length === 0;

  function Chip({ children }: { children: React.ReactNode }) {
    return (
      <span
        style={{
          padding: "4px 10px",
          borderRadius: 999,
          border: `1px solid ${UI.border}`,
          background: "rgba(255,255,255,0.03)",
          fontSize: 12,
          fontWeight: 900,
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
  function shortTs(v: string) {
    if (!v) return "-";
    const d = new Date(v);
    if (isNaN(d.getTime())) return v.slice(0, 10);
    return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
  }

  return (
    <section
      style={{
        marginTop: 16,
        padding: 16,
        border: `1px solid ${UI.border}`,
        borderRadius: 14,
        background: UI.panel,
        minHeight: 300,
      }}
    >
      <div style={{ display: "flex", justifyContent: "space-between", gap: 12, alignItems: "flex-start" }}>
        <div style={{ minWidth: 0 }}>
          <h2 style={{ margin: 0 }}>
            Top Movers <span style={{ color: UI.orange }}>(Social_link)</span>
          </h2>
          <div style={{ marginTop: 6, fontSize: 12, opacity: 0.75 }}>
            refresh: 10s · symbols: {chipCount || 0}
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
            Symbols: <span style={{ color: UI.orangeSoft }}>{chipCount || 0}</span>
          </Chip>
          <Chip>
            Refresh: <span style={{ color: UI.orangeSoft }}>10s</span>
          </Chip>
          <Chip>
            ts: <span style={{ color: UI.orangeSoft }}>{tsShort}</span>
          </Chip>
        </div>
      </div>

      {refreshing && !showSkeleton && (
        <p style={{ marginTop: 8, opacity: 0.7, fontSize: 12 }}>Actualizando…</p>
      )}

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
                  <td style={{ padding: "12px 8px" }}>
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
        <div style={{ marginTop: 12, overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr style={{ textAlign: "left", borderBottom: `1px solid ${UI.border}` }}>
                <th style={{ padding: "10px 8px", fontSize: 16, opacity: 0.75, fontWeight: 800 }}>Symbol</th>
                <th style={{ padding: "10px 8px", fontSize: 16, opacity: 0.75, fontWeight: 800 }}>Trend</th>
                <th style={{ padding: "10px 8px", fontSize: 16, opacity: 0.75, fontWeight: 800 }}>Score</th>
                <th style={{ padding: "10px 8px", fontSize: 16, opacity: 0.75, fontWeight: 800 }}>Reason</th>
              </tr>
            </thead>
            <tbody>
              {items.map((t) => {
                const c = trendColor(t.trend);
                const isHover = hover === t.symbol;

                return (
                  <tr
                    key={t.symbol}
                    onMouseEnter={() => setHover(t.symbol)}
                    onMouseLeave={() => setHover(null)}
                    style={{
                      borderBottom: `1px solid ${UI.borderSoft}`,
                      background: isHover ? "rgba(255,159,67,0.06)" : "transparent",
                      transition: "background 120ms ease",
                    }}
                  >
                    <td style={{ padding: "12px 8px", fontWeight: 950 }}>
                      <button
                        onClick={() => navigator.clipboard.writeText(t.symbol)}
                        style={{
                          all: "unset",
                          cursor: "pointer",
                          fontWeight: 950,
                        }}
                        title="Copiar simbolo"
                      >
                      <span
                        style={{
                          textShadow: isHover ? "0 0 10px rgba(255,159,67,0.18)" : "none",
                        }}
                      >
                        {t.symbol}
                      </span>
                      </button>
                    </td>

                    <td style={{ padding: "12px 8px" }}>
                      <div style={{ display: "inline-flex", alignItems: "center", gap: 10 }}>
                        <TrendBadge trend={t.trend} />
                        <span style={{ color: c, fontWeight: 900, fontSize: 12, opacity: 0.9 }}>
                          {t.trend.toUpperCase()}
                        </span>
                      </div>
                    </td>

                    <td style={{ padding: "12px 8px", fontWeight: 900 }}>
                      {typeof t.score === "number" ? t.score.toFixed(2) : "—"}
                    </td>

                    <td style={{ padding: "12px 8px", fontSize: 12, opacity: 0.85 }}>
                      {t.reason || "—"}
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
    </section>
  );
}
