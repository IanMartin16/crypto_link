"use client"

import { ReactNode, useState } from "react";
import { UI } from "@/lib/ui";
import Link from "next/link";
import { usePathname } from "next/navigation";

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

  const pathname = usePathname();

  const items = [
    { label: "Overview", href: "/dashboard" },
    { label: "Prices", href: "/dashboard/prices" },
    { label: "Trends", href: "/dashboard/trends" },
    { label: "Settings", href: "/dashboard/settings" },
    { label: "Symbols", href: "/dashboard/symbols" },
  ];

  const isActive = (href: string) => {
    if (href === "/dashboard") return pathname === "/dashboard";
    return pathname.startsWith(href);
  };

  <style jsx global>{`
    @keyframes pulseDot {
      0% { transform: scale(1); opacity: 0.75; }
      50% { transform: scale(1.35); opacity: 1; }
      100% { transform: scale(1); opacity: 0.75; }
    }
  `}</style>

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
              boxShadow: "0 0 18px rgba(255,159,67,0.12)",
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

        <nav style={{ marginTop: 18, display: "grid", gap: 8 }}>
          {items.map((item) => {
            const active = pathname === item.href;
            return (
              <Link 
                key={item.href} 
                href={item.href} 
                style={{ textDecoration: "none", color: "inherit" }}>
              <div
                style={{
                  padding: "10px 12px",
                  borderRadius: 12,
                  border: active ? "1px solid rgba(255,159,67,0.22)" : "1px solid rgba(255,255,255,0.08)",
                  background: active ? "rgba(255,159,67,0.08)" : "transparent",
                  cursor: "pointer",
                  fontWeight: active ? 900 : 650,
                  opacity: active ? 1 : 0.9,
                  transition: "all 140ms ease",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                }}
              >
                <span style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <span
                  style={{
                    width: 8,
                    height: 8,
                    borderRadius: 999,
                    background: active ? UI.orangeSoft : "rgba(255,255,255,0.18)",
                    boxShadow: active ? "0 0 12px rgba(255,159,67,0.28)" : "none",
                    transition: "all 140ms ease",
                }}
              />
                {item.label}
                </span>

                {/* barrita derecha cuando está activo */}
                <span
                  style={{
                    width: 10,
                    height: 10,
                    borderRadius: 999,
                    background: active ? UI.orangeSoft : "transparent",
                    boxShadow: active ? "0 0 14px rgba(255,159,67,0.32)" : "none",
                    }}
                  />
                </div>
              </Link>
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
    
        </div>
      </aside>

      {/* Main */}
      <main
        style={{
          padding: 24,
          position: "relative",
          overflow: "hidden",
        }}
      >
        <div
          aria-hidden
          style={{
            position: "absolute",
            inset: -200,
            background:
              "radial-gradient(600px 360px at 70% 10%, rgba(255,159,67,0.10), transparent 60%), radial-gradient(520px 320px at 20% 30%, rgba(46,229,157,0.06), transparent 60%)",
            pointerEvents: "none",
            filter: "blur(2px)",
          }}
      />
      <style jsx global>{`
        @keyframes clPulse {
        0% { transform: scale(1); opacity: 0.75; }
        50% { transform: scale(1.35); opacity: 1; }
        100% { transform: scale(1); opacity: 0.75; }
      }
    `}</style>
    <style>{`
        @keyframes clSweep {
          0%   { transform: translateX(-70%); opacity: .35; }
          40%  { opacity: .95; }
          100% { transform: translateX(70%);  opacity: .10; }
        }
      `}</style>
      <div style={{ position: "relative", zIndex: 1, maxWidth: 1250, margin: "0 auto" }}>
        {children}
      </div>
    </main>
    </div>
  );
}
