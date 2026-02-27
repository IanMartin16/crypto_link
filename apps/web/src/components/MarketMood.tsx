"use client";

import { UI } from "@/lib/ui";

type Insight = {
  line1: string;
  line2: string;
  divergence: boolean;
};

type Props = {
  score: number; // -100..100
  updatedAt?: string; // string ya resuelta desde el padre (client)
  confidence?: number;
  insight?: Insight;
};

function clamp(n: number, min: number, max: number) {
  return Math.max(min, Math.min(max, n));
}

function moodLabel(score: number) {
  if (score >= 60) return "STRONG BULLISH";
  if (score >= 25) return "BULLISH";
  if (score > -25) return "NEUTRAL";
  if (score > -60) return "BEARISH";
  return "STRONG BEARISH";
}

function moodTone(score: number, UI: any) {
  if (score > 20) return UI.green;
  if (score < -20) return UI.red;
  return "rgba(255,255,255,0.75)";
}

function toneColor(tone: "up" | "down" | "flat") {
  return tone === "up" ? UI.green : tone === "down" ? UI.red : UI.orangeSoft;
}
    

export default function MarketMood({ score, updatedAt, confidence, insight }: Props ) {
  const s = clamp(score, -100, 100);
  const label = moodLabel(s);
  const tone = moodTone(s, UI);
  const color = toneColor(tone);

  // -100..100  ->  0..100
  const pct = Math.max(0, Math.min(100, (score + 100) /2));

  const glow =
    tone === "up"
      ? "rgba(46,229,157,0.18)"
      : tone === "down"
      ? "rgba(255,107,107,0.18)"
      : "rgba(255,159,67,0.18)";

  function Gauge({ value, UI }: { value: number; UI: any }) {
  const v = Math.max(0, Math.min(1, value));
  const r = 14;
  const c = 2 * Math.PI * r;
  const dash = c * v;

  return (
    <svg width="36" height="36" viewBox="0 0 40 40">
      <circle cx="20" cy="20" r={r} stroke="rgba(255,255,255,0.10)" strokeWidth="4" fill="none" />
      <circle
        cx="20"
        cy="20"
        r={r}
        stroke={UI.orangeSoft}
        strokeWidth="4"
        fill="none"
        strokeDasharray={`${dash} ${c - dash}`}
        strokeLinecap="round"
        transform="rotate(-90 20 20)"
      />
    </svg>
  );
}    

  return (
    <div
      style={{
        padding: 18,
        borderRadius: 18,
        border: `1px solid ${UI.border}`,
        background:
          "linear-gradient(145deg, rgba(255,255,255,0.035), rgba(255,255,255,0.01))",
        boxShadow: `0 12px 40px rgba(0,0,0,0.35), 0 0 28px ${glow}`,
        marginBottom: 14,
      }}
    >
      <div style={{ display: "flex", justifyContent: "space-between", gap: 12, alignItems: "flex-start" }}>
        <div style={{ minWidth: 0 }}>
          <div style={{ fontSize: 12, opacity: 0.65, fontWeight: 900, letterSpacing: 0.35 }}>
            MARKET SENTIMENT
          </div>

          <div
            style={{
              marginTop: 6,
              fontSize: 28,
              fontWeight: 950,
              color,
              letterSpacing: 0.5,
              textShadow: "0 0 14px rgba(0,0,0,0.35)",
              whiteSpace: "nowrap",
              overflow: "hidden",
              textOverflow: "ellipsis",
            }}
            title={label}
          >
            {label}
          </div>

          <div style={{ marginTop: 6, display: "flex", gap: 10, flexWrap: "wrap" }}>
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
              
            </span>

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
              Updated: <span style={{ color: UI.orangeSoft }}>{updatedAt ?? "—"}</span>
            </span>
          </div>
        </div>

        <div style={{ textAlign: "right", minWidth: 120 }}>
          <div style={{ fontSize: 12, opacity: 0.65, fontWeight: 900 }}>Score</div>
          <div style={{ marginTop: 6, fontSize: 28, fontWeight: 950, color }}>
            {s.toFixed(0)}
          </div>
        </div>
      </div>

      {/* Gauge */}
      <div style={{ marginTop: 14 }}>
        <div
          style={{
            height: 10,
            borderRadius: 999,
            border: `1px solid ${UI.border}`,
            background: "rgba(255,255,255,0.04)",
            overflow: "hidden",
          }}
        >
          <div
            style={{
              width: `${pct}%`,
              height: "100%",
              background: `linear-gradient(90deg, ${color}, rgba(255,255,255,0.10))`,
              boxShadow: `0 0 16px ${glow}`,
              transition: "width 260ms ease",
            }}
          />
        </div>

        {typeof confidence === "number" && (<div>Confidence: {(confidence * 100).toFixed(0)}%</div>)}

        {insight && (
          <div style={{ marginTop: 12, display: "grid", gap: 6 }}>
          <div style={{ fontSize: 12, opacity: 0.85, lineHeight: 1.35 }}>
        {insight.line1}
          </div>
          <div
            style={{
              fontSize: 12,
              opacity: 0.8,
              lineHeight: 1.35,
              color: insight.divergence ? UI.red : "rgba(255,255,255,0.82)",
            }}
          >
            {insight.line2}
          </div>
        </div>
      )}

        <div style={{ marginTop: 6, display: "flex", justifyContent: "space-between", fontSize: 11, opacity: 0.6 }}>
          <span>Bearish</span>
          <span>Neutral</span>
          <span>Bullish</span>
        </div>
      </div>
    </div>
  );
}
