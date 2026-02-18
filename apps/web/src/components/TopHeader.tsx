"use client";

import { UI } from "@/lib/ui";

type ChipTone = "neutral" | "ok" | "warn";

function Chip({
  children,
  tone = "neutral",
}: {
  children: React.ReactNode;
  tone?: ChipTone;
}) {
  const border =
    tone === "ok" ? "rgba(46,229,157,0.28)" : tone === "warn" ? "rgba(255,107,107,0.28)" : UI.border;
  const bg =
    tone === "ok" ? "rgba(46,229,157,0.08)" : tone === "warn" ? "rgba(255,107,107,0.08)" : "rgba(255,255,255,0.03)";
  const color =
    tone === "ok" ? UI.green : tone === "warn" ? UI.red : "rgba(255,255,255,0.85)";

  return (
    <span
      style={{
        padding: "6px 10px",
        borderRadius: 999,
        border: `1px solid ${border}`,
        background: bg,
        fontSize: 12,
        fontWeight: 900,
        color,
        whiteSpace: "nowrap",
        maxWidth: "100%",
        overflow: "hidden",
        textOverflow: "ellipsis",
      }}
    >
      {children}
    </span>
  );
}

export default function TopHeader({
  title,
  subtitle,
  right,
}: {
  title: React.ReactNode;
  subtitle?: React.ReactNode;
  right?: React.ReactNode;
}) {
  return (
    <div
      style={{
        display: "flex",
        alignItems: "flex-start",
        justifyContent: "space-between",
        gap: 14,
        marginBottom: 14,
        padding: "12px 14px",
        borderRadius: 16,
        border: `1px solid ${UI.border}`,
        background: "rgba(255,255,255,0.02)",
        boxShadow: "0 10px 30px rgba(0,0,0,0.25)",
      }}
    >
      <div style={{ display: "flex", alignItems: "center", gap: 12, minWidth: 0 }}>
        <div
          style={{
            width: 44,
            height: 44,
            borderRadius: 14,
            display: "grid",
            placeItems: "center",
            border: `1px solid ${UI.border}`,
            background: "rgba(255,255,255,0.03)",
            boxShadow: "0 0 22px rgba(255,159,67,0.18)",
            overflow: "hidden",
          }}
        >
          <img
            src="/brand/cryptolink.png"
            alt="CryptoLink"
            style={{ width: 30, height: 30, objectFit: "contain" }}
          />
        </div>

        <div style={{ minWidth: 0 }}>
          <div style={{ fontSize: 26, fontWeight: 950, letterSpacing: 0.2, lineHeight: 1.1 }}>
            {title}
          </div>
          {subtitle && (
            <div style={{ marginTop: 4, fontSize: 12, opacity: 0.75, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
              {subtitle}
            </div>
          )}
        </div>
      </div>

      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", justifyContent: "flex-end", maxWidth: "52%", minWidth: 0 }}>
        {right}
      </div>
    </div>
  );
}

export { Chip };
