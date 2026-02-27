"use client";

import { useEffect, useMemo, useRef } from "react";
import { createChart, LineSeries, type IChartApi, type ISeriesApi, type UTCTimestamp } from "lightweight-charts";
import type { TrendItem } from "@/lib/types";

type Point = { time: UTCTimestamp; value: number };

export default function TrendChartPanel({
  items,
  maxPoints = 60,
}: {
  items: TrendItem[];
  maxPoints?: number;
}) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const seriesRef = useRef<ISeriesApi<"Line"> | null>(null);

  // ✅ histórico en memoria del componente
  const historyBySymbolRef = useRef<Map<string, Point[]>>(new Map());
  const lastTimeRef = useRef<UTCTimestamp | 0>(0);

  // Top trend (el #1)
  const top = items?.[0];

  const header = useMemo(() => {
    const sym = top?.symbol ?? "—";
    const score =
      typeof top?.score === "number" ? top.score.toFixed(2) : "—";
    return { sym, score };
  }, [top?.symbol, top?.score]);

  // 1) init chart
  useEffect(() => {
    if (!containerRef.current) return;

    const chart = createChart(containerRef.current, {
      height: 220,
      layout: {
        background: { color: "transparent" },
        textColor: "#b7b7b7",
      },
      grid: {
        vertLines: { visible: false },
        horzLines: { visible: false },
      },
      rightPriceScale: {
        borderVisible: false,
      },
      timeScale: {
        borderVisible: false,
        secondsVisible: true,
        timeVisible: true,
      },
      crosshair: {
        vertLine: { visible: true },
        horzLine: { visible: true },
      },
    });

    const series = chart.addSeries(LineSeries, {
      lineWidth: 2,
      // si luego quieres colores dinámicos por trend, lo hacemos aquí
      priceLineVisible: true,
      lastValueVisible: true,
    });

    chartRef.current = chart;
    seriesRef.current = series;

    const onResize = () => {
      if (!containerRef.current || !chartRef.current) return;
      chartRef.current.applyOptions({ width: containerRef.current.clientWidth });
    };

    // set width inicial
    chart.applyOptions({ width: containerRef.current.clientWidth });

    window.addEventListener("resize", onResize);
    return () => {
      window.removeEventListener("resize", onResize);
      chart.remove();
      chartRef.current = null;
      seriesRef.current = null;
    };
  }, []);

  // 2) cada vez que cambie el top score, añadimos un punto
  useEffect(() => {
    if (!seriesRef.current) return;
    if (!top || typeof top.score !== "number") return;

    const sym = (top.symbol || "").toUpperCase();
    if (!sym) return;

    const nowSec = Math.floor(Date.now() / 1000) as UTCTimestamp;

    // evita duplicar puntos si caen en el mismo segundo
    if (nowSec === lastTimeRef.current) return;
    lastTimeRef.current = nowSec;

    const next: Point = { time: nowSec, value: top.score };

    const map = historyBySymbolRef.current;
    const arr = map.get(sym) ?? [];
    arr.push(next);
    if (arr.length > maxPoints) arr.splice(0, arr.length - maxPoints);
    map.set(sym, arr);

    // ✅ pintamos el histórico del símbolo activo
    seriesRef.current.setData(arr);
    chartRef.current?.timeScale().fitContent();
  }, [top?.score, top?.symbol, maxPoints]);

  useEffect(() => {
    if (!seriesRef.current) return;
    const sym = (top?.symbol || "").toUpperCase();
    if (!sym) return;

    const arr = historyBySymbolRef.current.get(sym);
    if (arr?.length) {
      const color =
        score >= 15
          ? "#34d399"
          : score <= -15
          ? "#fb7185"
          : "#f59e0b";
seriesRef.current.applyOptions({ color });
      seriesRef.current.setData(arr);
      chartRef.current?.timeScale().fitContent();
    }
  }, [top?.symbol]);

  const score = typeof top?.score === "number" ? top.score : 0;

  const tone =
    score >= 15 ? "BULLISH"
    : score <= -15 ? "BEARISH"
    : "NEUTRAL";

  const toneCls =
    tone === "BULLISH"
      ? "border-emerald-400/30 bg-emerald-400/10 text-emerald-200"
      : tone === "BEARISH"
      ? "border-rose-400/30 bg-rose-400/10 text-rose-200"
      : "border-white/15 bg-white/5 text-white/70";

  return (
    <div className="rounded-xl border border-white/10 bg-white/[0.03] p-3">
      <div className="flex items-baseline justify-between gap-3 mb-2">
        <div className="min-w-0">
          <div className="flex items-center gap-2 text-xs font-semibold tracking-wide text-white/70">
            <span>Trend Pulse · {header.sym}</span>

            <span
              className={[
                "inline-flex items-center rounded-md border px-2 py-0.5 text-[11px] font-semibold transition-colors",
                toneCls,
              ].join(" ")}
            >
              {tone}
            </span>
          </div>
          <div className="text-[11px] text-white/45">
            Live score · {header.score}
          </div>
        </div>

        <div className="text-[11px] text-white/45">
          {maxPoints} pts
        </div>
      </div>

      <div ref={containerRef} className="relative" />
    </div>
  );
}