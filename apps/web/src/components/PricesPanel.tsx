"use client";

import { useEffect, useRef, useState } from "react";
import { UI } from "@/lib/ui";
import { getFiat } from "@/lib/fiatStore";
import { getApiKey } from "@/lib/apiKey";
import { getSymbols } from "@/lib/symbolsStore";
import { fetchPricesBatch } from "@/lib/cryptoLink";
import { Skeleton } from "@/components/Skeleton";

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

type Row = { symbol: string; fiat: string; price?: number; cache?: string; ok?: boolean; err?: string };

export default function PricesPanel({
  onRows,
  onHealth,
}: {
  onRows?: (rows: Row[]) => void;
  onHealth?: (h: { ok: boolean; lastOkAt?: string; lastErr?: string }) => void;
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

      return {
        symbol: item.symbol,
        fiat: res.fiat,
        price: typeof price === "number" ? price : undefined,
        cache: item.cache,
        ok: item.ok,
        err,
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
  console.log("[PricesPanel render] rows=", rows.length, "loading=", loading, "refreshing=", refreshing, "error=", error);
}, [rows, loading, refreshing, error]);


  useEffect(() => {
    load("initial");

    const syncTools = () => {
      setChipFiat(getFiat());
      setChipCount(getSymbols().length);
    };

    syncTools();
    window.addEventListener("cryptolink:fiat", syncTools as any);
    window.addEventListener("cryptolink:symbols", syncTools as any);

    const id = setInterval(() => load("refresh"), 5000);

    const onFiat = async () => {
      setUpdatingFiat(true);
      await load("refresh");
      setUpdatingFiat(false);
    };
    const onSymbols = () => setTimeout(() => load("refresh"), 0);

    window.addEventListener("cryptolink:fiat", onFiat as any);
    window.addEventListener("cryptolink:symbols", onSymbols as any);

    return () => {
      clearInterval(id);
      Object.values(flashTimersRef.current).forEach(clearTimeout);
      window.removeEventListener("cryptolink:fiat", onFiat as any);
      window.removeEventListener("cryptolink:symbols", onSymbols as any);
    };
  }, []);

  async function copySymbol(sym: string) {
    try {
      await navigator.clipboard.writeText(sym);
      setCopied(sym);
      setTimeout(() => setCopied(null), 900);
    } catch {
      // fallback viejo (por si clipboard no está disponible)
      const ta = document.createElement("textarea");
      ta.value = sym;
      document.body.appendChild(ta);
      ta.select();
      document.execCommand("copy");
      document.body.removeChild(ta);
      setCopied(sym);
      setTimeout(() => setCopied(null), 900);
    }
  }

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
        }}
      >
        {children}
      </span>
    );
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
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", gap: 12 }}>
        <h2 style={{ margin: 0 }}>
          Prices <span style={{ color: UI.orange }}>Batch</span>
        </h2>

        <div style={{ display: "flex", gap: 8, flexWrap: "wrap", justifyContent: "flex-end" }}>
          <Chip>
            Symbols: <span style={{ color: UI.orangeSoft }}>{chipCount}</span>
          </Chip>
          <Chip>
            Fiat: <span style={{ color: UI.orangeSoft }}>{chipFiat}</span>
          </Chip>
          <Chip>
            Refresh: <span style={{ color: UI.orangeSoft }}>5s</span>
          </Chip>
        </div>
      </div>

      <p style={{ marginTop: 8, opacity: 0.8 }}>BFF batch: 1 request / 5s.</p>
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
                <th style={{ padding: "10px 8px", fontSize: 12, opacity: 0.75, fontWeight: 800, textAlign: "right" }}>Price</th>
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
        <div style={{ marginTop: 12, overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr style={{ textAlign: "left", borderBottom: `1px solid ${UI.border}` }}>
                <th style={{ padding: "10px 8px", fontSize: 16, opacity: 0.75, fontWeight: 800 }}>Symbol</th>
                <th style={{ padding: "10px 8px", fontSize: 16, opacity: 0.75, fontWeight: 800 }}>Status</th>
                <th style={{ padding: "10px 8px", fontSize: 16, opacity: 0.75, fontWeight: 800, textAlign: "right" }}>Price</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => {
                const f = flashRow[r.symbol]; // ✅ aquí sí existe r

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
                      transition: "background 160ms ease",
                    }}
                  >
                  <td style={{ padding: "12px 8px", fontWeight: 900 }}>
                    <button
                      onClick={() => copySymbol(r.symbol)}
                      style={{
                        all: "unset",
                        cursor: "pointer",
                        fontWeight: 950,
                        display: "inline-flex",
                        alignItems: "center",
                        gap: 8,
                      }}
                        title="Copiar símbolo"
                      >
                        <span
                          style={{
                            textShadow: hover === r.symbol ? "0 0 10px rgba(255,159,67,0.20)" : "none",
                          }}
                        >
                         {r.symbol}
                        </span>

                        <span
                          style={{
                            fontSize: 11,
                            opacity: hover === r.symbol ? 0.85 : 0.0,
                            transition: "opacity 120ms ease",
                            color: UI.orangeSoft,
                          }}
                        >
                          copy
                        </span>

                        {copied === r.symbol && (
                        <span
                          style={{
                            fontSize: 11,
                            padding: "2px 8px",
                            borderRadius: 999,
                            border: `1px solid rgba(46,229,157,0.25)`,
                            background: "rgba(46,229,157,0.10)",
                            color: UI.green,
                            fontWeight: 900,
                          }}
                        >
                            copied
                          </span>
                        )}
                      </button>
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

                  <td style={{ padding: "12px 8px", textAlign: "right", fontWeight: 900, color: UI.orangeSoft }}>
                    {typeof r.price === "number" ? new Intl.NumberFormat("en-US", { 
                      style: "currency", 
                      currency: r.fiat || "USD", 
                      maximumFractionDigits: r.fiat === "JPY" ? 0 : 2,
                    }).format(r.price) :
                      "—"}
                  </td>
                </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
