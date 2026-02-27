"use client";

import type { PriceRow } from "@/lib/types";
import { getPriceHistory } from "@/lib/usePriceHistory";

function clsPct(p?: number) {
  if (typeof p !== "number") return "text-white/55";
  return p > 0 ? "text-emerald-300" : p < 0 ? "text-rose-300" : "text-white/70";
}

function sparkPath(values: number[], w = 90, h = 28) {
  if (!values || values.length < 2) return "";
  const min = Math.min(...values);
  const max = Math.max(...values);
  const span = max - min || 1;

  const step = w / (values.length - 1);
  return values
    .map((v, i) => {
      const x = i * step;
      const y = h - ((v - min) / span) * h;
      return `${i === 0 ? "M" : "L"} ${x.toFixed(2)} ${y.toFixed(2)}`;
    })
    .join(" ");
}

function strokeFor(p?: number) {
  if (typeof p !== "number") return "rgba(255,255,255,0.40)";
  if (p > 0) return "rgba(46,229,157,0.85)";
  if (p < 0) return "rgba(255,107,107,0.85)";
  return "rgba(255,255,255,0.55)";
}

export default function MarketSparkStrip({
  rows,
  max = 12,
  title = "MARKET PULSE",
}: {
  rows: PriceRow[];
  max?: number;
  title?: string;
}) {
  const items = (rows || []).slice(0, max);

  return (
    <div className="rounded-xl border border-white/10 bg-white/[0.03] p-3">
      <div className="flex items-center justify-between">
        <div className="text-xs font-semibold tracking-wide text-white/70">
          {title}
        </div>
        <div className="text-[11px] text-white/45">
          {items.length ? `${items.length} shown` : "—"}
        </div>
      </div>

      <div className="mt-3 grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-3">
        {items.map((r) => {
          const hist = getPriceHistory(r.symbol);
          const last = hist.length ? hist[hist.length - 1] : r.price;
          const first = hist.length ? hist[0] : r.prevPrice;
          const stroke = strokeFor(r.pct);

          const pct =
            typeof last === "number" &&
            typeof first === "number" &&
            first !== 0
              ? ((last - first) / first) * 100
              : r.pct;

          const path = sparkPath(hist.slice(-30), 90, 28); // últimas 30 muestras

          <div className="text-[10px] text-white/30">MarketSparkStrip v2</div>
          return (
            <div
              key={r.symbol}
              className="flex items-center justify-between rounded-lg border border-white/10 bg-white/[0.02] px-3 py-2"
            >
              <div className="min-w-0">
                <div className="text-xs font-semibold text-white/80">
                  {r.symbol}
                  <span className="ml-2 text-[11px] text-white/45">{r.fiat ?? ""}</span>
                </div>
                <div className={`text-[11px] font-semibold tabular-nums ${clsPct(pct)}`}>
                  {typeof pct === "number" ? `${pct > 0 ? "+" : ""}${pct.toFixed(2)}%` : "—"}
                </div>
              </div>

              <div className="ml-3 shrink-0">
                <svg width="90" height="28" viewBox="0 0 90 28">
                  <defs>
                    <filter id="glow">
                      <feGaussianBlur stdDeviation="1.2" result="coloredBlur" />
                      <feMerge>
                        <feMergeNode in="coloredBlur" />
                        <feMergeNode in="SourceGraphic" />
                      </feMerge>
                    </filter>
                  </defs>
                  <path
                    d={path || ""}
                    fill="none"
                    stroke={stroke}
                    strokeWidth="1.8"
                    filter={typeof pct === "number" ? "url(#glow)" : undefined}
                  />
                </svg>
              </div>
            </div>
          );
        })}

        {!items.length ? (
          <div className="text-sm text-white/50">Waiting for prices…</div>
        ) : null}
      </div>
    </div>
  );
}