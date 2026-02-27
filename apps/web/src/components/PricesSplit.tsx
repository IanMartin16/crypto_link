"use client";

import { useEffect, useMemo, useState } from "react";
import type { PriceRow } from "@/lib/types";
import SymbolCell from "@/components/SymbolCell";

function fmtPrice(n?: number) {
  if (typeof n !== "number") return "—";
  // compacto tipo terminal
  return n >= 1000 ? n.toFixed(0) : n >= 1 ? n.toFixed(2) : n.toFixed(6);
}

function PctCell({ pct }: { pct?: number }) {
  if (typeof pct !== "number") return <span className="text-white/45">—</span>;
  const cls =
    pct > 0 ? "text-emerald-300" : pct < 0 ? "text-rose-300" : "text-white/70";
  return (
    <span className={`font-semibold tabular-nums ${cls}`}>
      {pct > 0 ? "+" : ""}
      {pct.toFixed(2)}%
    </span>
  );
}

function sortForWatchlist(rows: PriceRow[]) {
  // estable: ok primero, luego symbol
  return [...rows].sort((a, b) => {
    const ao = a.ok ? 0 : 1;
    const bo = b.ok ? 0 : 1;
    if (ao !== bo) return ao - bo;
    return (a.symbol || "").localeCompare(b.symbol || "");
  });
}

function sortTopMovers(rows: PriceRow[], filter: "all" | "gainers" | "losers") {
  return [...rows]
    .filter((r) => typeof r.pct === "number")
    .filter((r) => {
      const p = r.pct ?? 0;
      if (filter === "gainers") return p > 0;
      if (filter === "losers") return p < 0;
      return true;
    })
    .sort((a, b) => {
      const ao = a.ok ? 0 : 1;
      const bo = b.ok ? 0 : 1;
      if (ao !== bo) return ao - bo;
      const ap = Math.abs(a.pct ?? 0);
      const bp = Math.abs(b.pct ?? 0);
      if (bp !== ap) return bp - ap;
      return (a.symbol || "").localeCompare(b.symbol || "");
    })
    .slice(0, 5);
}

export default function PricesSplit({
  rows,
  titleLeft = "WATCHLIST",
  titleRight = "TOP MOVERS",
}: {
  rows: PriceRow[];
  titleLeft?: string;
  titleRight?: string;
}) {
  const [moversFilter, setMoversFilter] = useState<"all" | "gainers" | "losers">("all");

  const movers = useMemo(
  () => sortTopMovers(rows || [], moversFilter),
  [rows, moversFilter]
);

  const watch = sortForWatchlist(rows || []);

  function fmtTick(rows: PriceRow[]) {
    const t = rows.find((r) => r.updatedAt)?.updatedAt;
    if (!t) return "—";
    return new Date(t).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
  }
  
<div className="text-[10px] text-white/30">PricesSplit v2</div>
  return (
    <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
      {/* LEFT: WATCHLIST */}
      <div className="lg:col-span-2 rounded-xl border border-white/10 bg-white/[0.03]">
        <div className="flex items-center justify-between px-3 py-2 border-b border-white/10">
          <div className="text-xs font-semibold tracking-wide text-white/70">
            {titleLeft}
          </div>
          <div className="text-[11px] text-white/45">
            {watch.length} assets
          </div>
        </div>

        <div className="max-h-[420px] overflow-auto">
          <table className="w-full">
            <thead className="sticky top-0 bg-black/20 backdrop-blur">
              <tr className="text-[11px] text-white/55">
                <th className="px-3 py-2 text-left font-medium">Asset</th>
                <th className="px-3 py-2 text-right font-medium">Price</th>
                <th className="px-3 py-2 text-right font-medium">Δ%</th>
              </tr>
            </thead>
            <tbody>
              {watch.map((r) => (
                <tr key={r.symbol} className="border-t border-white/5 hover:bg-white/[0.04]">
                  <td className="px-3 py-2">
                    <SymbolCell symbol={r.symbol} fiat={r.fiat} />
                  </td>
                  <td className="px-3 py-2 text-right tabular-nums text-white/85">
                    {fmtPrice(r.price)}
                  </td>
                  <td className="px-3 py-2 text-right">
                    <PctCell pct={r.pct} />
                  </td>
                </tr>
              ))}
              {!watch.length ? (
                <tr>
                  <td className="px-3 py-6 text-sm text-white/50" colSpan={3}>
                    No prices yet.
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </div>

      {/* RIGHT: TOP MOVERS */}
      <div className="rounded-xl border border-white/10 bg-white/[0.03] flex flex-col">
        <div className="flex items-center justify-between px-3 py-2 border-b border-white/10">
          <div className="text-xs font-semibold tracking-wide text-white/70">
            {titleRight}
          </div>

          <div className="flex items-center gap-2">
            <div className="flex gap-1">
              {(["all", "gainers", "losers"] as const).map((k) => {
              const active = moversFilter === k;
              const label = k === "all" ? "All" : k === "gainers" ? "▲" : "▼";
              return (
                <button
                  key={k}
                  onClick={() => setMoversFilter(k)}
                  className={[
                    "rounded-md border px-2 py-0.5 text-[11px] font-semibold transition",
                    active
                    ? "border-white/20 bg-white/10 text-white"
                    : "border-white/10 bg-white/[0.03] text-white/55 hover:bg-white/[0.06]",
                    ].join(" ")}
                  aria-pressed={active}
                  title={k}
                >
                  {label}
                </button>
              );
            })}
          </div>
        </div>

          <div className="text-[11px] text-white/45">
            {movers.length ? fmtTick(rows) : "—"}
          </div>
        </div>

        <div className="max-h-[420px] overflow-auto p-2 space-y-2">
          {movers.map((r) => (
            <div
              key={r.symbol}
              className="flex items-center justify-between rounded-lg border border-white/10 bg-white/[0.02] px-2 py-2"
            >
              <div className="min-w-0">
                <SymbolCell symbol={r.symbol} fiat={r.fiat} />
              </div>

              <div className="flex flex-col items-end leading-tight">
                <div className="text-sm tabular-nums text-white/85">
                  {fmtPrice(r.price)}
                </div>
                <div className="text-xs">
                  <PctCell pct={r.pct} />
                </div>
              </div>
            </div>
          ))}
          {!movers.length ? (
            <div className="px-2 py-4 text-sm text-white/50">
              Waiting for movers…
            </div>
          ) : null}
        </div>
      </div>
    </div>
    
  );
}