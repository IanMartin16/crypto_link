"use client";

function fmt(iso?: string) {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "—";
  return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

function Pill({
  label,
  h,
}: {
  label: string;
  h?: { ok: boolean; lastOkAt?: string; lastErr?: string };
}) {
  const ok = h?.ok === true;
  const color = ok ? "#2ee59d" : "#ff6b6b";
  const bg = ok ? "rgba(46,229,157,0.10)" : "rgba(255,107,107,0.10)";
  const border = ok ? "rgba(46,229,157,0.25)" : "rgba(255,107,107,0.25)";

  const last = ok ? fmt(h?.lastOkAt) : "—";
  const err = !ok ? (h?.lastErr ?? "unknown") : "";

  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        gap: 10,
        padding: "8px 12px",
        borderRadius: 14,
        border: `1px solid ${border}`,
        background: bg,
        minWidth: 240,
      }}
      title={!ok ? err : undefined}
    >
      <span style={{ color, fontWeight: 900 }}>●</span>

      <div style={{ display: "grid", gap: 2 }}>
        <div style={{ fontSize: 12, fontWeight: 900, letterSpacing: 0.2 }}>
          {label} <span style={{ color, fontWeight: 900 }}>{ok ? "OK" : "DOWN"}</span>
        </div>

        <div style={{ fontSize: 12, opacity: 0.8 }}>
          {ok ? (
            <>
              last ok: <span style={{ fontWeight: 900 }}>{last}</span>
            </>
          ) : (
            <>
              err:{" "}
              <span
                style={{
                  fontWeight: 800,
                  opacity: 0.9,
                  display: "inline-block",
                  maxWidth: 180,
                  whiteSpace: "nowrap",
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                  verticalAlign: "bottom",
                }}
              >
                {err}
              </span>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

export default function StatusBar({
  prices,
  trends,
}: {
  prices?: { ok: boolean; lastOkAt?: string; lastErr?: string };
  trends?: { ok: boolean; lastOkAt?: string; lastErr?: string };
}) {
  return (
    <div
      style={{
        marginTop: 10,
        display: "flex",
        gap: 12,
        flexWrap: "wrap",
        alignItems: "center",
      }}
    >
      <Pill label="Prices" h={prices} />
      <Pill label="Trends" h={trends} />

      <div
        style={{
          marginLeft: "auto",
          fontSize: 12,
          opacity: 0.7,
          padding: "6px 10px",
          borderRadius: 999,
          border: "1px solid rgba(255,159,67,0.20)",
          background: "rgba(255,159,67,0.08)",
          color: "#ffb86b",
        }}
      >
        LIVE · 5s cache · BFF
      </div>
    </div>
  );
}
