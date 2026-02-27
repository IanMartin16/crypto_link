"use client";

import { useState, useEffect, useMemo } from "react";
import PricesPanel from "@/components/PricesPanel";
import StatCards from "@/components/StatCards";
import TrendsTable from "@/components/TrendsTable";
import StatusBar from "@/components/StatusBar";
import { UI } from "@/lib/ui";
import type { Health } from "@/lib/health";
import { HEALTH_OK } from "@/lib/health";
import TopHeader, { Chip } from "@/components/TopHeader";
import MarketMood from "@/components/MarketMood";
import { buildMarketInsight } from "@/lib/insights";
import { computeMood } from "@/lib/moodEngine";
import { normalizeTrends } from "@/lib/trendEngine";
import type { PriceRow, TrendItem } from "@/lib/types";
import type { SnapshotKPIs } from "@/lib/types";
import { computeSnapshotKPIs } from "@/lib/snapshotEngine";
import MarketSnapshotBar from "@/components/MarketSnapshotBar";
import { buildInsightV2 } from "@/lib/insights";
import MarketSparkStrip from "@/components/MarketSparkStrip";


export default function DashboardPage() {
  const [rows, setRows] = useState<PriceRow[]>([]);
  const [pricesHealth, setPricesHealth] = useState<Health>(HEALTH_OK);

  const [trendsHealth, setTrendsHealth] = useState<Health>(HEALTH_OK);
  const [trendItems, setTrendItems] = useState<TrendItem[]>([]);

  const [moodUpdatedAt, setMoodUpdatedAt] = useState<string>("—");

  // ✅ 1) Normaliza trends UNA vez
  const normalizedTrends = useMemo(() => normalizeTrends(trendItems), [trendItems]);

  // ✅ 2) Mood UNA vez (source of truth)
  const mood = useMemo(() => computeMood(rows, normalizedTrends), [rows, normalizedTrends]);

  // ✅ 3) Insight “market sentiment”
  const moodInsight = useMemo(
    () =>
      buildMarketInsight({
        rows,
        moodScore: mood.score,
        trends: normalizedTrends,
      }),
    [rows, mood.score, normalizedTrends]
  );

  useEffect(() => {
  const sample = rows.slice(0, 6).map(r => ({
    sym: r.symbol,
    price: r.price,
    prev: r.prevPrice,
    pct: r.pct,
  }));
  console.log("[dash rows sample]", sample);
}, [rows]);

  // ✅ 4) Snapshot KPIs (MarketSnapshotBar)
  const snapshot: SnapshotKPIs = useMemo(
    () =>
      computeSnapshotKPIs({
        rows,
        trends: normalizedTrends,
        moodScore: mood.score,
        confidence: mood.confidence,
      }),
    [rows, normalizedTrends, mood.score, mood.confidence]
  );

  // ✅ 5) Insight narrativo V2
  const insight = useMemo(
    () =>
      buildInsightV2({
        mood,
        snapshot,
        rows,
        trends: normalizedTrends,
      }),
    [mood.score, mood.confidence, snapshot.updatedAt, rows, normalizedTrends]
  );

  // ✅ “last updated” del mood
  useEffect(() => {
    setMoodUpdatedAt(new Date().toLocaleTimeString());
  }, [mood.score, mood.confidence, normalizedTrends.length]);

  // ... resto de helpers + JSX

  function insightTone(score: number) {
  if (score >= 35) return "bull";
  if (score <= -35) return "bear";
  return "neutral";
}

function toneClasses(tone: "bull" | "bear" | "neutral") {
  switch (tone) {
    case "bull":
      return "border-emerald-500/30 bg-emerald-500/10 text-emerald-200";
    case "bear":
      return "border-rose-500/30 bg-rose-500/10 text-rose-200";
    default:
      return "border-white/10 bg-white/5 text-white/75";
  }
}

function toneLabel(tone: "bull" | "bear" | "neutral") {
  if (tone === "bull") return "BULLISH";
  if (tone === "bear") return "BEARISH";
  return "NEUTRAL";
}

function InsightCard({
  headline,
  summary,
  note,
  moodScore,
}: {
  headline: string;
  summary: string;
  note?: string;
  moodScore: number;
}) {
  const tone = insightTone(moodScore);

  return (
    <div className="w-full rounded-xl border border-white/10 bg-white/[0.03] px-3 py-2">
      <div className="flex items-start gap-3">
        {/* badge */}
        <div
          className={`shrink-0 rounded-lg border px-2 py-1 text-[11px] font-semibold tracking-wide ${toneClasses(
            tone
          )}`}
          title={`mood score: ${Math.round(moodScore)}`}
        >
          {toneLabel(tone)}
        </div>

        {/* text */}
        <div className="min-w-0">
          <div className="truncate text-sm font-semibold text-white/90">
            {headline}
          </div>
          <div className="mt-0.5 text-xs text-white/70">{summary}</div>
          {note ? (
            <div className="mt-1 text-xs text-white/50">{note}</div>
          ) : null}
        </div>
      </div>
    </div>
  );
}

  return (
    <>
      <TopHeader
        title={
      <>
        CryptoLink <span style={{ color: UI.orange }}>V2</span>
      </>
      }
      subtitle={"Dashboard · batch pricing · social movers"}
      right={
      <>
        <Chip>LIVE</Chip>
        <Chip>refresh: 5s</Chip>
        <Chip>batch BFF</Chip>
      </>
      }
    />
    <StatusBar prices={pricesHealth} trends={trendsHealth} />

    <div style={{ display: "grid", gap: 14 }}>
      <MarketMood
        score={mood.score}
        confidence={mood.confidence}
        updatedAt={moodUpdatedAt}
        insight={moodInsight}
      />
      <MarketSnapshotBar snapshot={snapshot} />
      
      <InsightCard
        headline={insight.headline}
        summary={insight.summary}
        note={insight.note}
        moodScore={mood.score}
      />

      <div style={{ marginTop: 12 }}>
        <StatCards rows={rows} />
      </div>
      <MarketSparkStrip rows={rows} max={12} />
      <div 
        style={{ 
          marginTop: 12, 
          display: "grid", 
          gridTemplateColumns: "repeat(2, minmax(0, 1fr))", 
          gap: 14,
          alignItems: "start" 
          }}
          >
           <PricesPanel onRows={setRows} onHealth={setPricesHealth}/>
           <TrendsTable onHealth={setTrendsHealth} onItems={setTrendItems} />
        </div>
      </div>  
    </>
  );
}
