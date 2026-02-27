"use client";

import { useEffect, useState } from "react";

import type { SnapshotKPIs, SnapshotKPI } from "@/lib/types";

function toneClasses(tone: SnapshotKPI["tone"]) {
  // Sin depender de colores específicos del theme; usa convenciones (success/warn/destructive)
  switch (tone) {
    case "good":
      return "border border-emerald-500/30 bg-emerald-500/10 text-emerald-200";
    case "warn":
      return "border border-amber-500/30 bg-amber-500/10 text-amber-200";
    case "bad":
      return "border border-rose-500/30 bg-rose-500/10 text-rose-200";
    default:
      return "border border-white/10 bg-white/5 text-white/80";
  }
}

export default function MarketSnapshotBar({
  snapshot,
}: {
  snapshot: SnapshotKPIs;
}) {
  const items = snapshot?.items ?? [];

  const [tick, setTick] = useState(0);

  useEffect(() => {
    const id = setInterval(() => setTick((t) => t + 1), 1000);
    return () => clearInterval(id);
  }, []);

  return (
    <div className="w-full rounded-xl border border-white/10 bg-white/[0.03] px-3 py-2">
      <div className="flex flex-wrap items-center gap-2">
        <div className="mr-1 text-xs font-semibold tracking-wide text-white/70">
          MARKET SNAPSHOT
        </div>

        <div className="flex flex-1 flex-wrap items-center gap-2">
          {items.map((kpi) => (
            <KPIChip key={kpi.key} kpi={kpi} />
          ))}
        </div>

        <div className="ml-auto text-[11px] text-white/45">
          updated {Math.max(0, Math.floor((Date.now() - snapshot.updatedAt) / 1000))}s ago
        </div>
      </div>
    </div>
  );
}

function KPIChip({ kpi }: { kpi: SnapshotKPI }) {
  return (
    <div className={`flex items-center gap-2 rounded-lg px-2 py-1 ${toneClasses(kpi.tone)}`}>
      <div className="text-[11px] font-medium uppercase tracking-wide opacity-80">
        {kpi.label}
      </div>
      <div className="text-sm font-semibold tabular-nums">{kpi.value}</div>
      {kpi.sub ? (
        <div className="hidden text-[11px] opacity-70 md:block">
          {kpi.sub}
        </div>
      ) : null}
    </div>
  );
}