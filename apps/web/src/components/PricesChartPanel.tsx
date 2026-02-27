"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import {
  createChart,
  LineSeries,
  type IChartApi,
  type ISeriesApi,
  type Time,
  type UTCTimestamp,
} from "lightweight-charts";

import type { PriceRow } from "@/lib/types";
import { usePriceHistory, getPriceHistory } from "@/lib/usePriceHistory";
import { AreaSeries } from "lightweight-charts";
import { getChartWatch } from "@/lib/chartWatchStore";

type ChartPoint = { t: number; price: number };

function buildPointsFromPrices(prices: number[], stepMs = 5000): ChartPoint[] {
  const now = Date.now();
  const start = now - Math.max(0, prices.length - 1) * stepMs;
  return prices.map((price, i) => ({ t: start + i * stepMs, price }));
}

function toSeries(points: ChartPoint[]) {
  return points.map((p) => ({
    time: Math.floor(p.t / 1000) as UTCTimestamp,
    value: p.price,
  }));
}

function normalizeTo100(prices: number[]) {
  const base = prices.find((x) => Number.isFinite(x) && x !== 0);
  if (!base) return prices;
  return prices.map((p) => (p / base) * 100);
}

function formatPrice(value: number, fiat: string) {
  try {
    return new Intl.NumberFormat("en-US", {
      style: "currency",
      currency: fiat,
      maximumFractionDigits: fiat === "JPY" ? 0 : value >= 1 ? 2 : 6,
    }).format(value);
  } catch {
    return `${fiat} ${value}`;
  }
}

function formatPct(pct: number) {
  const sign = pct > 0 ? "+" : "";
  return `${sign}${pct.toFixed(2)}%`;
}

export default function PriceChartPanel({
  rows,
  symbol,
  onSymbolChange,
}: {
  rows: PriceRow[];
  symbol: string;
  onSymbolChange: (s: string) => void;
}) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const [range, setRange] = useState<"5m" | "15m" | "50m">("15m");
  const [mode, setMode] = useState<"price" | "index">("price");
  const POPULAR_COMPARE = ["ETH", "SOL", "BNB", "XRP", "ADA", "DOGE"];

  const mainRef = useRef<ISeriesApi<"Area", Time> | null>(null);
  const overlaySeriesRef = useRef<Map<string, any>>(new Map());


  // UI state
  const [compare, setCompare] = useState<string[]>([]); // up to 2
  const [normalize, setNormalize] = useState(true);

  // crosshair info
  const [hover, setHover] = useState<{ price?: number; t?: number } | null>(null);

  // find current row for selected symbol
  const currentRow = useMemo(() => rows.find((r) => r.symbol === symbol), [rows, symbol]);
  const fiat = currentRow?.fiat ?? "USD";
  const unitLabel = mode === "index" ? "Index(100)" : fiat;
  const currentPrice = typeof currentRow?.price === "number" ? currentRow.price : undefined;

  // ✅ main series history (local hook)
  const mainPrices = usePriceHistory(symbol, currentPrice, 600);

   const pointsByRange = {
    "5m": 60,
    "15m": 180,
    "50m": 600,
  };

const maxPointsToShow = pointsByRange[range];
  // build main points/series
  const mainPoints = useMemo(() => {
    const sliced = mainPrices.slice(-maxPointsToShow);
    const values = mode === "index" ? toIndex100(sliced) : sliced;
    return buildPointsFromPrices(values, 5000);
  }, [mainPrices, maxPointsToShow, mode]);


  const rangeStats = useMemo(() => {
    const vals = (mainPoints || [])
      .map((p: any) => p?.value)
      .filter((v: any) => typeof v === "number" && Number.isFinite(v));

    if (vals.length < 2) return null;

    const first = vals[0];
    const last = vals[vals.length - 1];
    const high = Math.max(...vals);
    const low = Math.min(...vals);

    const deltaPct = first !== 0 ? ((last - first) / first) * 100 : 0;
    const volPct = last !== 0 ? ((high - low) / last) * 100 : 0;

    return { first, last, high, low, deltaPct, volPct };
  }, [mainPoints]);

  const mainSeriesData = useMemo(() => toSeries(mainPoints), [mainPoints]);

  const SERIES_COLORS = {
    main: "#3b82f6",   // azul
    ov1: "#10b981",    // verde
    ov2: "#f59e0b",    // naranja
  };

  const modeRef = useRef(mode);
  useEffect(() => { modeRef.current = mode; }, [mode]);

  const fiatRef = useRef(fiat);
  useEffect(() => { fiatRef.current = fiat; }, [fiat]);

  const [chartWatch, setChartWatch] = useState<string[]>([]);

  useEffect(() => {
    const sync = () => setChartWatch(getChartWatch());
    sync();
    window.addEventListener("cryptolink:chartwatch" as any, sync);
    return () => window.removeEventListener("cryptolink:chartwatch" as any, sync);
  }, []);

  const maxPoints = pointsByRange[range];

  const pinned = useMemo(() => {
    const main = symbol.toUpperCase();
    return chartWatch
       .map((s) => s.toUpperCase())
       .filter((s) => s && s !== main)
       .slice(0, 2);
  }, [chartWatch, symbol]);


const overlaysPoints = useMemo(() => {
  const out: Record<string, any[]> = {};

  for (const sym of pinned) {
    const prices = getPriceHistory(sym);
    const sliced = prices.slice(-maxPointsToShow);
    const values = mode === "index" ? toIndex100(sliced) : sliced;

    out[sym] = sliced.length ? buildPointsFromPrices(values, 5000) : [];
  }

  return out;
}, [pinned, maxPointsToShow, mode]);


  // symbols list (for dropdowns)
  const symbols = useMemo(() => {
    return [...new Set((rows || []).map((r) => r.symbol))].sort();
  }, [rows]);

  // keep compare list valid if symbols change
  useEffect(() => {
    const set = new Set(symbols);
    setCompare((prev) => prev.filter((s) => set.has(s) && s !== symbol).slice(0, 2));
  }, [symbols, symbol]);

  // init chart once
  useEffect(() => {
    if (!containerRef.current) return;

  <div className="flex items-center gap-4 px-3 pb-2 text-[11px]">
    <div className="flex items-center gap-1">
      <span className="h-2 w-2 rounded-full" style={{ background: SERIES_COLORS.main }} />
      <span className="text-white/70">{symbol}</span>
    </div>

    {compare[0] && (
      <div className="flex items-center gap-1">
        <span className="h-2 w-2 rounded-full" style={{ background: SERIES_COLORS.ov1 }} />
        <span className="text-white/60">{compare[0]}</span>
      </div>
    )}

    {compare[1] && (
      <div className="flex items-center gap-1">
        <span className="h-2 w-2 rounded-full" style={{ background: SERIES_COLORS.ov2 }} />
        <span className="text-white/60">{compare[1]}</span>
      </div>
    )}
  </div>

    const chart: IChartApi = createChart(containerRef.current, {
      height: 240,
      layout: {
      background: { color: "transparent" },
      textColor: "rgba(255,255,255,0.65)",
    },
    grid: {
      vertLines: { color: "rgba(255,255,255,0.06)" },
      horzLines: { color: "rgba(255,255,255,0.06)" },
    },
    rightPriceScale: { borderColor: "rgba(255,255,255,0.10)" },
    timeScale: {
      borderColor: "rgba(255,255,255,0.10)",
      timeVisible: true,
      secondsVisible: false,
    },
    crosshair: {
      vertLine: { color: "rgba(255,255,255,0.20)" },
      horzLine: { color: "rgba(255, 255, 255, 0.49)" },
    },
  });

  // 👇 watermark va aquí en v5
  (chart as any).applyOptions({
    watermark: { visible: false },
  });

  const container = containerRef.current;
    if (!container) return;

  const tooltip = document.createElement("div");
  tooltip.style.position = "absolute";
  tooltip.style.zIndex = "50";
  tooltip.style.pointerEvents = "none";
  tooltip.style.display = "none";
  tooltip.style.padding = "8px 10px";
  tooltip.style.borderRadius = "10px";
  tooltip.style.border = "1px solid rgba(255,255,255,0.12)";
  tooltip.style.background = "rgba(10,10,12,0.75)";
  tooltip.style.backdropFilter = "blur(8px)";
  tooltip.style.color = "rgba(255,255,255,0.92)";
  tooltip.style.fontSize = "12px";
  tooltip.style.boxShadow = "0 8px 24px rgba(0,0,0,0.35)";

  containerRef.current?.appendChild(tooltip);

  const modeRef = { current: mode };
  const fiatRef = { current: fiat };
  
  chart.subscribeCrosshairMove((param) => {
    if (!param?.time || !param?.point) {
      tooltip.style.display = "none";
      return;
    }

    const { x, y } = param.point;

    // bounds check
    const w = container.clientWidth;
    const h = container.clientHeight;
    if (x < 0 || x > w || y < 0 || y > h) {
      tooltip.style.display = "none";
      return;
    }

    const sd = param.seriesData.get(mainRef.current!);
    const value = (sd as any)?.value as number | undefined;

    const t = typeof param.time === "number"
      ? new Date(param.time * 1000)
      : new Date((param.time as any).year, (param.time as any).month - 1, (param.time as any).day);

    const timeTxt = t.toLocaleTimeString();
    const dateTxt = t.toLocaleDateString();

    const valTxt =
      typeof value === "number"
        ? (mode === "index" ? value.toFixed(2) : formatPrice(value, fiat))
        : "—";

    tooltip.innerHTML = `
      <div style="font-weight:800;margin-bottom:2px;">${valTxt}</div>
      <div style="opacity:.75;font-size:11px;">${timeTxt}</div>
      <div style="opacity:.55;font-size:11px;">${dateTxt}</div>
    `;

    tooltip.style.display = "block";

    // position (avoid overflow)
    const left = Math.min(x + 12, w - 180);
    const top = Math.min(y + 12, h - 70);

    tooltip.style.left = `${left}px`;
    tooltip.style.top = `${top}px`;
  });

    const main = chart.addSeries(AreaSeries, {
      lineWidth: 2,
      lineColor: SERIES_COLORS.main,
      topColor: "rgba(255,255,255,0.10)",
      bottomColor: "rgba(255,255,255,0.00)",
      priceLineVisible: true,
      lastValueVisible: true,
    });

    const ov1 = chart.addSeries(LineSeries, {
      lineWidth: 3,
      color: SERIES_COLORS.ov1,
      priceLineVisible: true,
      lastValueVisible: true,
    });

    const ov2 = chart.addSeries(LineSeries, {
      lineWidth: 3,
      color: SERIES_COLORS.ov2,
      priceLineVisible: true,
      lastValueVisible: true,
    });

    chartRef.current = chart;
    mainRef.current = main;

    // ✅ crosshair -> info bar
    chart.subscribeCrosshairMove((p) => {
      const series = mainRef.current;
      if (!series) return;

      const v: any = p.seriesData.get(series);
      const price = typeof v?.value === "number" ? v.value : undefined;
      const t = p.time ? Number(p.time) * 1000 : undefined;

      setHover({ price, t });
    });

    const onResize = () => {
      if (!containerRef.current || !chartRef.current) return;
      chartRef.current.applyOptions({ width: containerRef.current.clientWidth });
    };

    onResize();
    window.addEventListener("resize", onResize);

    return () => {
      window.removeEventListener("resize", onResize);
      setHover(null);
      chart.remove();
      chartRef.current = null;
      mainRef.current = null;
    };
  }, []);

  useEffect(() => {
    const hasCompare = Boolean(compare?.[0] || compare?.[1]);

    // si hay compare, arranca en index; si no, vuelve a price
    setMode((prev) => {
      if (hasCompare && prev === "price") return "index";
      if (!hasCompare && prev === "index") return "price";
      return prev;
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [compare?.[0], compare?.[1]]);

  // update main series
  useEffect(() => {
  if (!mainRef.current) return;

  mainRef.current.setData(mainSeriesData);
  chartRef.current?.timeScale().fitContent();

  // ✅ overlays
  const map = overlaySeriesRef.current;
  for (const sym of pinned) {
    const series = map.get(sym);
    if (!series) continue;

    const pts = overlaysPoints[sym] ?? [];
    series.setData(toSeries(pts));
  }
}, [mainSeriesData, pinned, overlaysPoints]);

  // update overlays (from global history)
  useEffect(() => {

    const s1 = compare[0];
    const s2 = compare[1];

    const p1 = s1 ? getPriceHistory(s1).slice(-maxPointsToShow) : [];
    const p2 = s2 ? getPriceHistory(s2).slice(-maxPointsToShow) : [];

    const a1 = normalize ? normalizeTo100(p1) : p1;
    const a2 = normalize ? normalizeTo100(p2) : p2;

    const raw1 = s1 ? getPriceHistory(s1).slice(-maxPointsToShow) : [];
    const raw2 = s2 ? getPriceHistory(s2).slice(-maxPointsToShow) : [];

    const v1 = mode === "index" ? toIndex100(raw1) : raw1;
    const v2 = mode === "index" ? toIndex100(raw2) : raw2;

  }, [compare, mode, maxPointsToShow, normalize, rows]); // rows cambia cada tick → overlays se refrescan

  const toggleCompare = (sym: string) => {
  const key = sym.toUpperCase();

  // no compares contra el main
  if (key === symbol.toUpperCase()) return;

  setCompare((prev) => {
    const has = prev.includes(key);

    // si ya está, lo quita
    if (has) return prev.filter((x) => x !== key);

    // si hay espacio, lo agrega
    if (prev.length < 2) return [...prev, key];

    // si ya hay 2, reemplaza el último (comportamiento simple y útil)
    return [prev[0], key];
  });
};

const clearCompare = () => setCompare([]);
  // info bar values
  const last = currentPrice;
  const shown = hover?.price ?? last;

  const deltaPct = useMemo(() => {
    if (typeof shown !== "number" || typeof last !== "number" || last === 0) return undefined;
    return ((shown - last) / last) * 100;
  }, [shown, last]);

  const deltaTone =
    typeof deltaPct === "number"
      ? deltaPct > 0
        ? "text-emerald-300"
        : deltaPct < 0
          ? "text-rose-300"
          : "text-white/70"
      : "text-white/45";

  function toIndex100(prices: number[]) {
    const base = prices.find((x) => Number.isFinite(x) && x !== 0);
    if (!base) return prices;
    return prices.map((p) => (p / base) * 100);
  }    

  // compare setters (max 2, no duplicates, no same as main)
  function setCompare1(v: string) {
    const next = [v, compare[1]].filter(Boolean).filter((s, i, a) => a.indexOf(s) === i);
    setCompare(next.filter((s) => s !== symbol).slice(0, 2));
  }
  function setCompare2(v: string) {
    const next = [compare[0], v].filter(Boolean).filter((s, i, a) => a.indexOf(s) === i);
    setCompare(next.filter((s) => s !== symbol).slice(0, 2));
  }

  const compareOptions1 = symbols.filter((s) => s !== symbol);
  const compareOptions2 = symbols.filter((s) => s !== symbol && s !== compare[0]);

  function fmtStat(v?: number) {
    if (typeof v !== "number") return "—";
    return mode === "index" ? v.toFixed(2) : formatPrice(v, fiat);
  }

  function pickOverlayColor(sym: string) {
    const palette = [
      "rgba(255,255,255,0.55)",
      "rgba(255,159,67,0.85)",
      "rgba(46,229,157,0.85)",
      "rgba(84,160,255,0.85)",
      "rgba(255,107,107,0.85)",
      "rgba(125,95,255,0.85)",
      "rgba(0,210,211,0.85)",
    ];
    let h = 0;
    for (let i = 0; i < sym.length; i++) h = (h * 31 + sym.charCodeAt(i)) >>> 0;
    return palette[h % palette.length];
  }

  useEffect(() => {
    const chart = chartRef.current;
    if (!chart) return;

    const map = overlaySeriesRef.current;

    // remove
    for (const [sym, series] of map.entries()) {
      if (!pinned.includes(sym)) {
        chart.removeSeries(series);
        map.delete(sym);
      }
    }

    // add
    for (const sym of pinned) {
      if (map.has(sym)) continue;

      const series = chart.addSeries(LineSeries, {
        lineWidth: 1,
        color: pickOverlayColor(sym),
        priceLineVisible: true,  // ✅ tu “línea de cotización”
        lastValueVisible: true,
      });

      map.set(sym, series);
    }
  }, [pinned]);

  return (
  <div className="rounded-xl border border-white/10 bg-white/[0.03]">
    {/* HEADER */}
    <div className="border-b border-white/10 px-3 py-2">
      <div className="flex items-start justify-between gap-3">
        {/* LEFT: title + quick compare */}
        <div className="min-w-0">
          <div className="text-xs font-semibold tracking-wide text-white/70">
            <span className="truncate">{symbol}</span>
            <span className="text-white/45"> · {unitLabel}</span>

            {compare.length ? (
              <span className="ml-2 text-[11px] text-white/45">
                vs {compare.join(" + ")}
              </span>
            ) : null}
          </div>

          {/* Quick compare chips */}
          <div className="mt-1 flex flex-wrap items-center gap-1">
            <span className="text-[11px] text-white/45 mr-1">Compare:</span>

            {POPULAR_COMPARE.map((s) => {
              const active = compare.includes(s);
              const disabled = s === symbol.toUpperCase();

              return (
                <button
                  key={s}
                  onClick={() => toggleCompare(s)}
                  disabled={disabled}
                  className={[
                    "rounded-md border px-2 py-0.5 text-[11px] font-semibold transition",
                    disabled
                      ? "border-white/5 bg-white/[0.02] text-white/25 cursor-not-allowed"
                      : active
                      ? "border-white/25 bg-white/10 text-white"
                      : "border-white/10 bg-white/[0.03] text-white/55 hover:bg-white/[0.06]",
                  ].join(" ")}
                  title={
                    disabled
                      ? "Already selected as main"
                      : active
                      ? "Remove overlay"
                      : "Add overlay"
                  }
                >
                  {active ? `✓ ${s}` : `+${s}`}
                </button>
              );
            })}

            {compare.length > 0 && (
              <button
                onClick={clearCompare}
                className="ml-2 rounded-md border border-white/10 bg-white/[0.03] px-2 py-0.5 text-[11px] font-semibold text-white/55 hover:bg-white/[0.06]"
                title="Clear overlays"
              >
                Clear
              </button>
            )}
          </div>

          {/* Last shown / hover / delta */}
          <div className="mt-1 text-[11px] text-white/45 tabular-nums">
            {typeof shown === "number"
              ? `@ ${mode === "index" ? shown.toFixed(2) : formatPrice(shown, fiat)}`
              : "warming…"}
            {hover?.t ? ` • ${new Date(hover.t).toLocaleTimeString()}` : ""}
            {typeof deltaPct === "number" ? (
              <span className={`ml-2 font-semibold ${deltaTone}`}>
                {formatPct(deltaPct)}
              </span>
            ) : null}
          </div>
        </div>

        {/* RIGHT: controls */}
        <div className="flex flex-col items-end gap-2">
          <div className="flex flex-wrap justify-end items-center gap-2">
            <label className="hidden sm:flex items-center gap-1 text-[11px] text-white/45">
              <input
                type="checkbox"
                checked={normalize}
                onChange={(e) => setNormalize(e.target.checked)}
              />
              normalize
            </label>

            {/* Mode */}
            <div className="flex gap-1">
              {(["price", "index"] as const).map((m) => {
                const active = mode === m;
                return (
                  <button
                    key={m}
                    onClick={() => setMode(m)}
                    className={[
                      "rounded-md border px-2 py-0.5 text-[11px] font-semibold transition",
                      active
                        ? "border-white/20 bg-white/10 text-white"
                        : "border-white/10 bg-white/[0.03] text-white/55 hover:bg-white/[0.06]",
                    ].join(" ")}
                    title={m === "price" ? "Real price" : "Index price"}
                  >
                    {m === "price" ? "Price" : "Index"}
                  </button>
                );
              })}
            </div>

            {/* Range */}
            <div className="flex gap-1">
              {(["5m", "15m", "50m"] as const).map((r) => {
                const active = range === r;
                return (
                  <button
                    key={r}
                    onClick={() => setRange(r)}
                    className={[
                      "rounded-md border px-2 py-0.5 text-[11px] font-semibold transition",
                      active
                        ? "border-white/20 bg-white/10 text-white"
                        : "border-white/10 bg-white/[0.03] text-white/55 hover:bg-white/[0.06]",
                    ].join(" ")}
                  >
                    {r}
                  </button>
                );
              })}
            </div>

            {/* Dropdowns (opcional, si quieres mantenerlos) */}
            <select
              value={symbol}
              onChange={(e) => onSymbolChange(e.target.value)}
              className="rounded-lg border border-white/10 bg-black/20 px-2 py-1 text-xs text-white/80 outline-none"
            >
              {symbols.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>

            <select
              value={compare[0] ?? ""}
              onChange={(e) => setCompare1(e.target.value)}
              className="rounded-lg border border-white/10 bg-black/20 px-2 py-1 text-xs text-white/80 outline-none"
            >
              <option value="">+ compare</option>
              {compareOptions1.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>

            <select
              value={compare[1] ?? ""}
              onChange={(e) => setCompare2(e.target.value)}
              disabled={!compare[0]}
              className="rounded-lg border border-white/10 bg-black/20 px-2 py-1 text-xs text-white/80 outline-none disabled:opacity-40"
            >
              <option value="">+ compare</option>
              {compareOptions2.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </div>
        </div>
      </div>
      {/* STATS (debajo del header, no dentro de los botones) */}
      {rangeStats ? (
        <div className="mt-2 grid grid-cols-2 gap-2 sm:grid-cols-4">
          <div className="rounded-lg border border-white/10 bg-white/[0.02] px-3 py-2">
            <div className="mt-2 px-3 text-[11px] text-white/35">High</div>
              Gathering data for status...
            <div className="text-sm font-semibold text-white/85 tabular-nums">
              {fmtStat(rangeStats.high)}
            </div>
          </div>

          <div className="rounded-lg border border-white/10 bg-white/[0.02] px-3 py-2">
            <div className="text-[10px] text-white/45">Low</div>
            <div className="text-sm font-semibold text-white/85 tabular-nums">
              {fmtStat(rangeStats.low)}
            </div>
          </div>

          <div className="rounded-lg border border-white/10 bg-white/[0.02] px-3 py-2">
            <div className="text-[10px] text-white/45">Δ Range</div>
            <div
              className={[
                "text-sm font-semibold tabular-nums",
                rangeStats.deltaPct > 0
                  ? "text-emerald-300"
                  : rangeStats.deltaPct < 0
                  ? "text-rose-300"
                  : "text-white/75",
              ].join(" ")}
            >
              {rangeStats.deltaPct > 0 ? "+" : ""}
              {rangeStats.deltaPct.toFixed(2)}%
            </div>
          </div>

          <div className="rounded-lg border border-white/10 bg-white/[0.02] px-3 py-2">
            <div className="text-[10px] text-white/45">Volatility</div>
            <div className="text-sm font-semibold text-white/85 tabular-nums">
              {rangeStats.volPct.toFixed(2)}%
            </div>
          </div>
        </div>
      ) : null}
    </div>

    {/* CHART */}
    <div className="px-2 py-2">
      <div ref={containerRef} className="relative h-[300px]" />
    </div>
  
  </div>
);
}