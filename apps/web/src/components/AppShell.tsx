import { ReactNode, useState } from "react";
import { UI } from "@/lib/ui";

type NavItem = { label: string; active?: boolean; hint?: string };

export default function AppShell({ children }: { children: ReactNode }) {
  const [hover, setHover] = useState<string | null>(null);

  const nav: NavItem[] = [
    { label: "Overview", active: true, hint: "Dashboard" },
    { label: "Prices", hint: "Batch + cache" },
    { label: "Trends", hint: "Social_link" },
    { label: "Alerts", hint: "Soon" },
    { label: "Billing", hint: "Soon" },
    { label: "Settings", hint: "Soon" },
  ];

  return (
    <div
      style={{
        minHeight: "100vh",
        display: "grid",
        gridTemplateColumns: "268px 1fr",
        background: UI.bg,
        color: UI.text,
        fontFamily: "system-ui",
      }}
    >
      <aside
        style={{
          borderRight: `1px solid ${UI.border}`,
          padding: 18,
          background: "rgba(255,255,255,0.02)",
        }}
      >
        {/* Brand */}
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <div
            style={{
              width: 36,
              height: 36,
              borderRadius: 14,
              display: "grid",
              placeItems: "center",
              border: `1px solid ${UI.border}`,
              background: "rgba(255,255,255,0.03)",
              boxShadow: "0 0 18px rgba(255,159,67,0.18)",
              overflow: "hidden",
            }}
          >
            <img
              src="/brand/cryptolink.png"
              alt="CL"
              style={{ width: 22, height: 22, objectFit: "contain" }}
            />
          </div>

          <div>
            <div style={{ fontSize: 16, fontWeight: 950, letterSpacing: 0.2 }}>
              CryptoLink <span style={{ color: UI.orange }}>V2</span>
            </div>
            <div style={{ marginTop: 2, fontSize: 12, opacity: 0.7 }}>Dashboard</div>
          </div>
        </div>

        {/* Workspace card */}
        <div
          style={{
            marginTop: 16,
            padding: 12,
            borderRadius: 16,
            border: `1px solid ${UI.border}`,
            background: "rgba(255,255,255,0.02)",
          }}
        >
          <div style={{ fontSize: 12, opacity: 0.7 }}>Workspace</div>
          <div style={{ marginTop: 6, fontWeight: 900 }}>Evilink Devs</div>

          <div style={{ marginTop: 10, display: "flex", gap: 8, flexWrap: "wrap" }}>
            <span
              style={{
                padding: "2px 10px",
                borderRadius: 999,
                fontSize: 12,
                fontWeight: 800,
                border: `1px solid rgba(255,159,67,0.22)`,
                background: "rgba(255,159,67,0.10)",
                color: UI.orangeSoft,
              }}
            >
              batch
            </span>
            <span
              style={{
                padding: "2px 10px",
                borderRadius: 999,
                fontSize: 12,
                fontWeight: 800,
                border: `1px solid rgba(46,229,157,0.22)`,
                background: "rgba(46,229,157,0.10)",
                color: UI.green,
              }}
            >
              live
            </span>
            <span
              style={{
                padding: "2px 10px",
                borderRadius: 999,
                fontSize: 12,
                fontWeight: 800,
                border: `1px solid ${UI.border}`,
                background: "rgba(255,255,255,0.03)",
                opacity: 0.9,
              }}
            >
              social_link
            </span>
          </div>
        </div>

        {/* Nav */}
        <nav style={{ marginTop: 16, display: "grid", gap: 8 }}>
          {nav.map((item) => {
            const active = !!item.active;
            const isHover = hover === item.label;

            return (
              <div
                key={item.label}
                onMouseEnter={() => setHover(item.label)}
                onMouseLeave={() => setHover(null)}
                style={{
                  padding: "10px 12px",
                  borderRadius: 14,
                  border: `1px solid ${UI.border}`,
                  background: active
                    ? "rgba(255,159,67,0.10)"
                    : isHover
                    ? "rgba(255,255,255,0.05)"
                    : "transparent",
                  cursor: "default",
                  fontWeight: active ? 900 : 700,
                  opacity: active ? 1 : 0.9,
                  boxShadow: active ? "0 0 22px rgba(255,159,67,0.12)" : "none",
                  transition: "background 120ms ease, box-shadow 120ms ease, transform 120ms ease",
                  transform: isHover && !active ? "translateX(1px)" : "translateX(0px)",
                }}
              >
                <div style={{ display: "flex", justifyContent: "space-between", gap: 10 }}>
                  <span>{item.label}</span>
                  {item.hint && (
                    <span style={{ fontSize: 12, opacity: active ? 0.85 : 0.6 }}>{item.hint}</span>
                  )}
                </div>
              </div>
            );
          })}
        </nav>

        {/* Footer tip */}
        <div
          style={{
            marginTop: 16,
            padding: 12,
            borderRadius: 14,
            border: `1px solid ${UI.border}`,
            background: "rgba(255,255,255,0.02)",
            fontSize: 12,
            opacity: 0.75,
            lineHeight: 1.4,
          }}
        >
          Tip: mañana hacemos navegación real (Overview/Prices/Trends) sin librerías pesadas.
        </div>
      </aside>

      {/* Main */}
      <main style={{ padding: 24 }}>
        <div style={{ maxWidth: 1250, margin: "0 auto" }}>
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            gap: 12,
            marginBottom: 14,
            padding: "10px 12px",
            borderRadius: 14,
            border: `1px solid ${UI.border}`,
            background: "rgba(255,255,255,0.02)",
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <span style={{ fontWeight: 950 }}>Overview</span>
            <span style={{ fontSize: 12, opacity: 0.7 }}>batch pricing · social movers</span>
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <span
              style={{
                padding: "3px 10px",
                borderRadius: 999,
                border: `1px solid rgba(255,159,67,0.22)`,
                background: "rgba(255,159,67,0.10)",
                color: UI.orangeSoft,
                fontSize: 12,
                fontWeight: 900,
              }}
            >
              cryptolink.mx
            </span>
            <span style={{ fontSize: 12, opacity: 0.75 }}>local</span>
          </div>
        </div>
        {children}</div>
      </main>
    </div>
  );
}
