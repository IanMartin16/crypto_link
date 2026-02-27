"use client";

import type { PriceRow } from "@/lib/types";

function tone(pct?: number) {
  if (typeof pct !== "number") return "rgba(255,255,255,0.10)";
  if (pct > 1) return "rgba(46,229,157,0.55)";
  if (pct > 0) return "rgba(46,229,157,0.30)";
  if (pct < -1) return "rgba(255,107,107,0.55)";
  if (pct < 0) return "rgba(255,107,107,0.30)";
  return "rgba(255,255,255,0.12)";
}

export default function MarketPulse({
  rows,
  max = 20,
  title = "MARKET PULSE",
}: {
  rows: PriceRow[];
  max?: number;
  title?: string;
}) {
  const items = [...(rows || [])]
    .filter((r) => r && r.symbol)
    .slice(0, max);

  return (
    <div className="rounded-xl border border-white/10 bg-white/[0.03] p-3">
      <div className="flex items-center justify-between">
        <div className="text-xs font-semibold tracking-wide text-white/70">
          {title}
        </div>
        <div className="text-[11px] text-white/45">
          {items.length ? `${items.length} assets` : "—"}
        </div>
      </div>

      <div className="mt-3 grid grid-cols-10 gap-2">
        {items.map((r) => (
          <div
            key={r.symbol}
            title={`${r.symbol}${typeof r.pct === "number" ? ` • ${r.pct.toFixed(2)}%` : ""}`}
            className="rounded-lg border border-white/10 px-2 py-2 text-[11px] font-semibold text-white/80"
            style={{
              background: tone(r.pct),
              backdropFilter: "blur(6px)",
            }}
          >
            <div className="truncate">{r.symbol}</div>
            <div className="mt-0.5 text-[10px] text-white/70 tabular-nums">
              {typeof r.pct === "number" ? `${r.pct > 0 ? "+" : ""}${r.pct.toFixed(1)}%` : "—"}
            </div>
          </div>
        ))}

        {!items.length ? (
          <div className="col-span-10 py-3 text-sm text-white/50">
            Waiting for prices…
          </div>
        ) : null}
      </div>
    </div>
  );
}