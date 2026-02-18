"use client";

import { useEffect, useRef, useState } from "react";
import { UI } from "@/lib/ui";
import { getFiat } from "@/lib/fiatStore";
import { getApiKey } from "@/lib/apiKey";
import { getSymbols } from "@/lib/symbolsStore";
import { fetchPricesBatch } from "@/lib/cryptoLink";
import { Skeleton } from "@/components/Skeleton";
import Toast from "@/components/Toast";
import Chip from "@/components/ui/Chip";
import { formatMoney, shortTime } from "@/lib/format";
import type { Health } from "@/lib/health";

// Si ya tienes CacheBadge en otro archivo, importa el tuyo.
// Aquí lo dejo inline para que no falle.
function CacheBadge({ v }: { v?: string }) {
  const val = (v ?? "MISS").toUpperCase();

  const isHit = val === "CACHE" || val === "HIT";
  const isLive = val === "LIVE";

  const s = isHit
    ? { bg: "rgba(46,229,157,0.10)", c: UI.green, b: "rgba(46,229,157,0.25)", dot: UI.green, label: "HIT" }
    : isLive
    ? { bg: "rgba(255,159,67,0.10)", c: UI.orangeSoft, b: "rgba(255,159,67,0.20)", dot: UI.orangeSoft, label: "LIVE" }
    : { bg: "rgba(255,255,255,0.06)", c: "#bbb", b: "rgba(255,255,255,0.10)", dot: "#bbb", label: "MISS" };

  return (
    <span
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: 8,
        padding: "2px 10px",
        borderRadius: 999,
        fontSize: 12,
        fontWeight: 900,
        background: s.bg,
        color: s.c,
        border: `1px solid ${s.b}`,
        letterSpacing: 0.2,
        whiteSpace: "nowrap",
      }}
    >
      <span
        style={{
          width: 8,
          height: 8,
          borderRadius: 999,
          background: s.dot,
          boxShadow: `0 0 10px ${s.dot}33`,
        }}
      />
      {s.label}
    </span>
  );
}

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

type Row = { symbol: string; fiat: string; price?: number; cache?: string; ok?: boolean; err?: string; ts?: string; source?: string; updatedAt?: string };

export default function PricesPanel({
  onRows,
  onHealth,
}: {
  onRows?: (rows: Row[]) => void;
  onHealth?: (h: Health) => void;
}) {
  const [rows, setRows] = useState<Row[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [updatingFiat, setUpdatingFiat] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const reqSeq = useRef(0);
  const showSkeleton = loading && rows.length === 0;
  const [hover, setHover] = useState<string | null>(null);
  const [flashRow, setFlashRow] = useState<Record<string, "up" | "down" >>({});
  const lastPriceRef = useRef<Record<string, number>>({});
  const flashTimersRef = useRef<Record<string, any>>({});
  const [chipFiat, setChipFiat] = useState<string>("-");
  const [chipCount, setChipCount] = useState<number>(0);
  const [copied, setCopied] = useState<string | null>(null);
  const [auto, setAuto] = useState(true);
  const [lastOkAt, setLastOkAt] = useState<string | undefined>(undefined);
  const [toast, setToast] = useState<{ msg: string; tone?: "ok" | "warn" | "err" } | null>(null);
  const [lastUpdated, setLastUpdated] = useState<string>("");
  const [symbolsCount, setSymbolsCount] = useState(0);
  const [fiatLabel, setFiatLabel] = useState("USD");
  

async function load(kind: "initial" | "refresh" = "refresh") {
  const seq = ++reqSeq.current;

  try {
    setError(null);
    if (kind === "initial") setLoading(true);
    else setRefreshing(true);

    const apiKey = getApiKey();
    if (!apiKey) {
      setRows([]);
      setError("Falta API Key (pégala arriba).");
      return;
    }

    const fiat = getFiat();
    const symbols = getSymbols();

    if (kind !== "initial" && (!symbols || symbols.length === 0)) return;

    const res = await fetchPricesBatch(symbols, fiat, apiKey);
    if (seq !== reqSeq.current) return;

    const mapped: Row[] = (res.data ?? []).map((item: any) => {
      const d = item.data;
      const price = d?.price ?? d?.data?.price ?? d?.value ?? d?.rate;
      const err = d?.error || d?.message || (!item.ok ? "upstream_error" : undefined);

      const ts = d?.ts ?? d?.data?.ts;
      const source = d?.source ?? d?.data?.source;
      
      return {
        symbol: item.symbol,
        fiat: res.fiat,
        price: typeof price === "number" ? price : undefined,
        cache: item.cache,
        ok: item.ok,
        err,
        ts,
        source,
        updatedAt: new Date().toISOString(),
      };
    });

    if (kind !== "initial" && mapped.length === 0) return;

    // ✅ construye nextRows SIN setState anidado
    const prevMap = new Map(rows.map((r) => [r.symbol, r]));

    const nextRows: Row[] = mapped.map((cur) => {
      const old = prevMap.get(cur.symbol);
      const price = typeof cur.price === "number" ? cur.price : old?.price;
      return { ...old, ...cur, price };
    });

    // ✅ flashes: fuera de setRows
    const newFlashes: Record<string, "up" | "down"> = {};

    for (const cur of nextRows) {
      if (typeof cur.price !== "number") continue;

      const last = lastPriceRef.current[cur.symbol];
      if (typeof last === "number") {
        if (cur.price > last) newFlashes[cur.symbol] = "up";
        else if (cur.price < last) newFlashes[cur.symbol] = "down";
      }
      lastPriceRef.current[cur.symbol] = cur.price;
    }

    setRows(nextRows);

    if (Object.keys(newFlashes).length) {
      setFlashRow((prev) => ({ ...prev, ...newFlashes }));

      for (const sym of Object.keys(newFlashes)) {
        if (flashTimersRef.current[sym]) clearTimeout(flashTimersRef.current[sym]);

        flashTimersRef.current[sym] = setTimeout(() => {
          setFlashRow((m) => {
            const copy = { ...m };
            delete copy[sym];
            return copy;
          });
        }, 450);
      }
    }
    setLastUpdated(new Date().toISOString());

    // ✅ notifica al padre SIN “setState during render”
    queueMicrotask(() => {
      if (seq !== reqSeq.current) return;
      onRows?.(nextRows);
      onHealth?.({ ok: true, lastOkAt: new Date().toISOString() });
    });
  } catch (e: any) {
    if (seq !== reqSeq.current) return;
    const msg = e?.message ?? "Error cargando precios";
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
  }, [rows, loading, refreshing, error]);


  useEffect(() => {
    load("initial");

    const updateMeta = () => {
      const s = getSymbols();
      setSymbolsCount(s.length);
      setFiatLabel(getFiat());
    };

    updateMeta();

    window.addEventListener("cryptolink:fiat", updateMeta as any);
    window.addEventListener("cryptolink:symbols", updateMeta as any);

    const id = setInterval(() => load("refresh"), 5000);

    window.addEventListener("cryptolink:fiat", updateMeta as any);
    window.addEventListener("cryptolink:symbols", updateMeta as any);

    const onFiat = async () => {
      setUpdatingFiat(true);
      await load("refresh");
      setUpdatingFiat(false);
    };
    const onSymbols = () => setTimeout(() => load("refresh"), 0);

    return () => {
      clearInterval(id);
      Object.values(flashTimersRef.current).forEach(clearTimeout);
      window.removeEventListener("cryptolink:fiat", onFiat as any);
      window.removeEventListener("cryptolink:symbols", onSymbols as any);
    };
  }, []);
  
   useEffect(() => {
      if (!auto) return;

      const id = setInterval(() => load("refresh"), 5000);
      return () => clearInterval(id);
      // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auto]);

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

  function copySymbol(sym: string) {
    navigator.clipboard.writeText(sym);
    setCopied(sym);
    setTimeout(() => setCopied(null), 1200);
  }

  function shortTime(v?: string) {
    if (!v) return "—";
    const d = new Date(v);
    if (isNaN(d.getTime())) return "—";
    return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" }); 
  }

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
        }}
      >
        {children}
      </span>
    );
  }

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
      
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", gap: 12 }}>
        <h2 style={{ margin: 0, fontSize: 18, fontWeight: 900, letterSpacing: 0.3}}>
          Prices <span style={{ color: UI.orange }}>Batch</span>
        </h2>

        <div style={{ display: "flex", gap: 8, flexWrap: "wrap", justifyContent: "flex-end" }}>

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
              title="Pausar / reanudar auto refresh"
            >
              {auto ? "ON" : "OFF"}
            </button>
          </Chip>
          <Chip>
            Symbols: <span style={{ color: UI.orangeSoft }}>{symbolsCount}</span>
          </Chip>
          <Chip>
            Fiat: <span style={{ color: UI.orangeSoft }}>{fiatLabel}</span>
          </Chip>
          <Chip>
            Updated:{" "}
            <span style={{ color: UI.orangeSoft }}>
              {lastUpdated
                ? new Date(lastUpdated).toLocaleTimeString([], {
                    hour: "2-digit",
                    minute: "2-digit",
                    second: "2-digit",
                  })
                : "--"}
            </span>
          </Chip>
          <Chip>
            <span style={{ display: "inline-flex", alignItems: "center", gap: 8 }}>
              <RefreshDot on={auto && refreshing } />
              {refreshing || loading ? "updating" : "idle"}
            </span>
          </Chip>
        </div>
      </div>
      <p style={{ marginTop: 6, fontSize: 11, opacity: 0.65 }}>BFF batch: 1 request / 5s.</p>
      {refreshing && !loading && 
      <p style={{ marginTop: 8, opacity: 0.7, fontSize: 12 }}>
        Actualizando…
      </p>
      }

      {updatingFiat && <p style={{ opacity: 0.8 }}>Actualizando moneda…</p>}
      {error && <p style={{ color: UI.red }}>Error: {error}</p>}

      {showSkeleton &&(
        <div style={{ marginTop: 12 }}>
          ...skeleton...
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr style={{ textAlign: "left", borderBottom: `1px solid ${UI.border}` }}>
                <th style={{ padding: "10px 8px", fontSize: 12, opacity: 0.75, fontWeight: 800}}>Symbol</th>
                <th style={{ padding: "10px 8px", fontSize: 12, opacity: 0.75, fontWeight: 800 }}>Status</th>
                <th style={{ padding: "10px 8px", fontSize: 12, opacity: 0.75, fontWeight: 800 }}>Price</th>
                <th style={{ padding: "8px 6px" }}>Updated</th>
                <th style={{ padding: "8px 6px" }}>Source</th>
              </tr>
            </thead>
            <tbody>
              {[...Array(6)].map((_, i) => (
                <tr key={i} style={{ borderBottom: `1px solid ${UI.borderSoft}` }}>
                  <td style={{ padding: "10px 6px" }}>
                    <Skeleton w={60} h={12} />
                  </td>
                  <td style={{ padding: "12px 8px" }}>
                    <Skeleton w={90} h={12} r={999} />
                  </td>
                  <td style={{ padding: "12px 8px", textAlign: "right" }}>
                    <Skeleton w={120} h={12} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {rows.length > 0 && (
        <div style={{ marginTop: 12, overflowX: "auto", maxHeight: 420 }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr style={{ textAlign: "left", borderBottom: `1px solid ${UI.border}`, position: "sticky", top: 0, background: UI.panel, zIndex: 1, }}>
                <th style={{ padding: "10px 8px", fontSize: 12, opacity: 0.75, fontWeight: 800 }}>Symbol</th>
                <th style={{ padding: "10px 8px", fontSize: 12, opacity: 0.75, fontWeight: 800 }}>Status</th>
                <th style={{ padding: "10px 8px", fontSize: 12, opacity: 0.75, fontWeight: 800 }}>Price</th>
                <th style={{ padding: "10px 8px", fontSize: 12, opacity: 0.75, fontWeight: 800 }}>Updated</th>
                <th style={{ padding: "10px 8px", fontSize: 12, opacity: 0.75, fontWeight: 800 }}>Source</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r, idx) => {
                const f = flashRow[r.symbol]; // ✅ aquí sí existe r
                const zebra = idx % 2 === 0 ? "rgba(255,255,255,0.02)" : "transparent";
                const isHover = hover === r.symbol;
                const bg =
                  isHover
                  ? "rgba(255,159,67,0.07)"
                  : zebra;

               return (
                <tr
                  key={r.symbol}
                  onMouseEnter={() => setHover(r.symbol)}
                  onMouseLeave={() => setHover(null)}
                  style={{
                    borderBottom: `1px solid ${UI.borderSoft}`,
                    background:
                      hover === r.symbol
                        ? "rgba(255,159,67,0.06)"
                        : f === "up"
                        ? "rgba(46,229,157,0.08)"
                        : f === "down"
                        ? "rgba(255,107,107,0.08)"
                        : "transparent",
                      transition: "background 160ms ease, transform 140ms ease",
                      transform: isHover ? "translateY(-1px)" : "translateY(0)",
                    }}
                  >
                  <td style={{ padding: "12px 8px", fontWeight: 950 }}>
                    <div
                      style={{ display: "flex", alignItems: "center", gap: 6, cursor: "pointer" }}
                      onClick={() => copySymbol(r.symbol)}
                    >
                      <span
                        style={{
                          textShadow: hover === r.symbol ? "0 0 10px rgba(255,159,67,0.18)" : "none",
                        }}
                      >
                        {r.symbol}
                      </span>

                      {hover === r.symbol && (
                        <span
                          style={{
                            width: 22,
                            height: 22,
                            borderRadius: 8,
                            display: "grid",
                            placeItems: "center",
                            border: `1px solid ${UI.border}`,
                            background: "rgba(255,255,255,0.03)",
                            opacity: 0.85,
                          }}
                          title="Copiar símbolo"
                        >
                          ⧉
                        </span>
                      )}
                    </div>
                  </td>
                  <td style={{ padding: "12px 8px" }}>
                    <div style={{ display: "inline-flex", alignItems: "center", gap: 10 }}>
                      <CacheBadge v={r.cache} />
                      {r.ok === false && (
                        <span style={{ color: UI.red, fontSize: 12, fontWeight: 700 }}>
                          provider issue
                        </span>
                      )}
                    </div>
                  </td>

                  <td style={{ padding: "12px 8px", fontWeight: 900, color: UI.orangeSoft }}>
                    {typeof r.price === "number" ? formatMoney(r.price, r.fiat) : "—"}
                  </td>

                  <td style={{ padding: "12px 8px", opacity: 0.85, fontSize: 12 }}>
                    {shortTime(r.updatedAt ?? r.ts)}
                  </td>

                  <td style={{ padding: "12px 8px", opacity: 0.85, fontSize: 12 }}>
                    {r.source ?? "—"}
                  </td>
                </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    <Toast toast={toast} onClear={() => setToast(null)} />
      {copied && (
        <div
          style={{
            position: "fixed",
            bottom: 24,
            right: 24,
            padding: "10px 14px",
            borderRadius: 999,
            border: `1px solid ${UI.border}`,
            background: "rgba(10,14,20,0.92)",
            boxShadow: "0 0 18px rgba(255,159,67,0.22)",
            fontSize: 12,
            fontWeight: 900,
            color: UI.orangeSoft,
            zIndex: 999,
            display: "inline-flex",
            alignItems: "center",
            gap: 10,
            animation: "toastIn 160ms ease-out",
          }}
        >
          <span
            style={{
              width: 20,
              height: 20,
              borderRadius: 999,
              display: "grid",
              placeItems: "center",
              background: "rgba(255,159,67,0.14)",
              border: "1px solid rgba(255,159,67,0.20)",
            }}
          >
            {/* icono copy ultra simple */}
            <span style={{ fontSize: 12, lineHeight: 1 }}>⧉</span>
          </span>

          <span>
            <span style={{ color: "#fff" }}>{copied}</span> copiado
          </span>

          <style>{`
            @keyframes toastIn {
              from { opacity: 0; transform: translateY(6px) scale(0.98); }
              to   { opacity: 1; transform: translateY(0)   scale(1); }
            }
          `}</style>
        </div>
      )}
    </section>
  );
}
