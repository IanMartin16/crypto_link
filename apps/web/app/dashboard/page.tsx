"use client";

import { useState } from "react";
import ApiKeyBar from "@/components/ApiKeyBar";
import FiatToggle from "@/components/FiatToggle";
import PricesPanel from "@/components/PricesPanel";
import StatCards from "@/components/StatCards";
import TrendsTable from "@/components/TrendsTable";
import SymbolsEditor from "@/components/SymbolsEditor";
import StatusBar from "@/components/StatusBar";
import { UI } from "@/lib/ui";
import type { Health } from "@/lib/health";
import { HEALTH_OK } from "@/lib/health";
import TopHeader, { Chip } from "@/components/TopHeader";

export default function DashboardPage() {
  const [rows, setRows] = useState<any[]>([]);
  const [pricesHealth, setPricesHealth] = useState<Health>(HEALTH_OK);
  const [trendsHealth, setTrendsHealth] = useState<Health>(HEALTH_OK);

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
    </>
  );
}
