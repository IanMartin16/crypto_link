"use client";

import { getChartWatch, toggleChartWatch } from "@/lib/chartWatchStore";
import { useEffect, useMemo, useState } from "react";

export default function SymbolChips({
  symbols,
  selected,
  onToggle,
}: {
  symbols: string[];
  selected: string[];
  onToggle: (sym: string) => void;
}) {
  const sel = useMemo(() => new Set(selected.map((x) => x.toUpperCase())), [selected]);

  const [chartWatch, setChartWatch] = useState<string[]>([]);

  useEffect(() => {
    const sync = () => setChartWatch(getChartWatch());
    sync();

    window.addEventListener("cryptolink:chartwatch" as any, sync);
    return () => window.removeEventListener("cryptolink:chartwatch" as any, sync);
  }, []);

  const onToggleChart = (symbol: string) => {
    const next = toggleChartWatch(symbol);
    setChartWatch(next);
    window.dispatchEvent(new Event("cryptolink:chartwatch"));
  };

  return (
    <div className="flex flex-wrap gap-2">
      {symbols.map((s) => {
        const key = s.toUpperCase();
        const active = sel.has(key);

        const inChart = chartWatch.includes(key);
        const full = chartWatch.length >= 5 && !inChart;

        return (
          <div key={s} className="flex items-center gap-1">
            {/* chip normal */}
            <button
              onClick={() => onToggle(s)}
              className={[
                "rounded-full px-3 py-1 text-xs font-semibold border transition",
                active
                  ? "border-white/20 bg-white/10 text-white"
                  : "border-white/10 bg-white/[0.03] text-white/60 hover:bg-white/[0.06]",
              ].join(" ")}
              aria-pressed={active}
              title={active ? "Selected" : "Select"}
            >
              {active ? "● " : ""}
              {s}
            </button>

            {/* pin to chart (max 5) */}
            <button
              onClick={() => onToggleChart(s)}
              disabled={full}
              className={[
                "rounded-full px-2 py-1 text-[11px] font-semibold border transition",
                full
                  ? "border-white/5 bg-white/[0.02] text-white/25 cursor-not-allowed"
                  : inChart
                  ? "border-white/20 bg-white/10 text-white"
                  : "border-white/10 bg-white/[0.03] text-white/55 hover:bg-white/[0.06]",
              ].join(" ")}
              title={full ? "Max 5 pinned to chart" : inChart ? "Remove from chart" : "Pin to chart"}
              aria-pressed={inChart}
            >
              {inChart ? "✓" : "📈"}
            </button>
          </div>
        );
      })}
    </div>
  );
}