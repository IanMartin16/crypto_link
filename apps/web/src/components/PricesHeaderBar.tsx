"use client";

import type { PriceRow } from "@/lib/types";
import type { Health } from "@/lib/health";

function fmtTime(iso?: string) {
  if (!iso) return "—";
  const d = new Date(iso);
  return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
}

export default function PricesHeaderBar({
  rows,
  health,
  fiat,
  assetsCount,
  lastUpdated,
}: {
  rows: PriceRow[];
  health?: Health;
  fiat: string;
  assetsCount: number;
  lastUpdated?: string;
}) {
  const okCount = rows.filter((r) => r.ok).length;
  const errCount = rows.length - okCount;

  const statusTone =
    health?.ok === true
      ? "border-emerald-400/25 bg-emerald-400/10 text-emerald-200"
      : health?.ok === false
      ? "border-rose-400/25 bg-rose-400/10 text-rose-200"
      : "border-white/10 bg-white/[0.03] text-white/70";

  const statusLabel =
    health?.ok === true ? "LIVE" : health?.ok === false ? "DEGRADED" : "—";

  return (
    <div className="rounded-xl border border-white/10 bg-white/[0.03] px-3 py-2">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2 min-w-0">
          <span className="text-xs font-semibold tracking-wide text-white/70">
            PRICES
          </span>

          <span className={["inline-flex items-center rounded-md border px-2 py-0.5 text-[11px] font-semibold", statusTone].join(" ")}>
            {statusLabel}
          </span>

          <span className="text-[11px] text-white/45">
            Updated {fmtTime(lastUpdated)}
          </span>
        </div>

        <div className="flex flex-wrap items-center gap-2 text-[11px] text-white/55">
          <span className="rounded-md border border-white/10 bg-white/[0.02] px-2 py-0.5">
            Fiat: <span className="text-white/80 font-semibold">{fiat}</span>
          </span>

          <span className="rounded-md border border-white/10 bg-white/[0.02] px-2 py-0.5">
            Assets: <span className="text-white/80 font-semibold">{assetsCount}</span>
          </span>

          <span className="rounded-md border border-white/10 bg-white/[0.02] px-2 py-0.5">
            OK: <span className="text-white/80 font-semibold">{okCount}</span>
          </span>

          <span className="rounded-md border border-white/10 bg-white/[0.02] px-2 py-0.5">
            ERR: <span className="text-white/80 font-semibold">{errCount}</span>
          </span>
        </div>
      </div>
    </div>
  );
}