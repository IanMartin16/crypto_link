"use client";

import { useState } from "react";
import ApiKeyBar from "@/components/ApiKeyBar";
import FiatToggle from "@/components/FiatToggle";
import TrendsRouteBody from "@/components/TrendsRouteBody";
import TrendsTable from "@/components/TrendsTable";
import StatusBar from "@/components/StatusBar";
import type { Health } from "@/lib/health";
import { HEALTH_OK } from "@/lib/health";

export default function TrendsPage() {
  const [trendsHealth, setTrendsHealth] = useState<Health>(HEALTH_OK);
  return (
    <div>
      <div style={{ display: "flex", alignItems: "baseline", justifyContent: "space-between", gap: 12 }}>
        <h1 style={{ margin: 0 }}>Trends</h1>
        <div style={{ fontSize: 12, opacity: 0.7 }}>Social_link · 10s refresh</div>
      </div>

      <div style={{ marginTop: 12, display: "grid", gridTemplateColumns: "repeat(2, minmax(0, 1fr))", gap: 12 }}>
        <ApiKeyBar />
        <FiatToggle />
      </div>
      <TrendsRouteBody />
    </div>
  );
}
