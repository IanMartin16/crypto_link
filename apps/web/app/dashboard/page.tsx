"use client";

import { useState } from "react";
import AppShell from "@/components/AppShell";
import ApiKeyBar from "@/components/ApiKeyBar";
import FiatToggle from "@/components/FiatToggle";
import PricesPanel from "@/components/PricesPanel";
import StatCards from "@/components/StatCards";
import TrendsTable from "@/components/TrendsTable";
import SymbolsEditor from "@/components/SymbolsEditor";
import StatusBar from "@/components/StatusBar";
import { UI } from "@/lib/ui";

export default function DashboardPage() {
  const [rows, setRows] = useState<any[]>([]);
  const [pricesHealth, setPricesHealth] = useState({ ok: true } as any);
  const [trendsHealth, setTrendsHealth] = useState({ ok: true } as any);

  return (
    <AppShell>
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          gap: 12,
          marginBottom: 14,
          padding: "12px 14px",
          borderRadius: 16,
          border: `1px solid ${UI.border}`,
          background: "rgba(255,255,255,0.02)",
          boxShadow: "0 10px 30px rgba(0,0,0,0.25)",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
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

        <div>
          <div style={{ fontSize: 26, fontWeight: 950, letterSpacing: 0.2 }}>
            CryptoLink <span style={{ color: UI.orange }}>V2</span>
          </div>
        <div style={{ fontSize: 12, opacity: 0.75 }}>
            Dashboard · batch pricing · social movers
        </div>
      </div>
    </div>

    {/* lado derecho: “pill” */}
    <div
      style={{
        padding: "8px 12px",
        borderRadius: 999,
        border: `1px solid ${UI.border}`,
        background: "rgba(255,255,255,0.03)",
        display: "flex",
        gap: 8,
        alignItems: "center",
        fontSize: 12,
        opacity: 0.9,
      }}
    >
      <span style={{ color: UI.orangeSoft, fontWeight: 900 }}>LIVE</span>
      <span style={{ opacity: 0.6 }}>·</span>
      <span style={{ opacity: 0.85 }}>5s refresh</span>
    </div>
  </div>

    <div style={{ display: "grid", gap: 14 }}>
      <StatusBar prices={pricesHealth} trends={trendsHealth} />

      <div style={{ marginTop: 12, display: "grid", gridTemplateColumns: "repeat(2, minmax(0, 1fr))", gap: 12 }}>
        <ApiKeyBar />
        <FiatToggle />
      </div>

      <div style={{ marginTop: 12 }}>
        <StatCards rows={rows} />
      </div>

      <div 
        style={{ 
          marginTop: 12, 
          display: "grid", 
          gridTemplateColumns: "repeat(3, minmax(0, 1fr))", 
          gap: 14,
          alignItems: "start" 
          }}
          >
           <PricesPanel onRows={setRows} onHealth={setPricesHealth}/>
           <TrendsTable onHealth={setTrendsHealth}/>
          <SymbolsEditor />
        </div>
      </div>  
    </AppShell>
  );
}
