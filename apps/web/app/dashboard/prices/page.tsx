"use client";

import { useState } from "react";
import ApiKeyBar from "@/components/ApiKeyBar";
import FiatToggle from "@/components/FiatToggle";
import PricesRouteBody from "@/components/PricesRouteBody";
import StatusBar from "@/components/StatusBar";
import PricesPanel from "@/components/PricesPanel";
import type { Health } from "@/lib/health";
import { HEALTH_OK } from "@/lib/health";

export default function PricesPage() {
  const [pricesHealth, setPricesHealth] = useState<Health>(HEALTH_OK);

  return (
    <div>
      <div style={{ display: "flex", alignItems: "baseline", justifyContent: "space-between", gap: 12 }}>
        <h1 style={{ margin: 0 }}>Prices</h1>
        <div style={{ fontSize: 12, opacity: 0.7 }}>Batch BFF · 5s refresh</div>
      </div>

      <div style={{ marginTop: 12, display: "grid", gridTemplateColumns: "repeat(2, minmax(0, 1fr))", gap: 12 }}>
        <ApiKeyBar />
        <FiatToggle />
      </div>
      {/* status + hero cards + panel */}
      <PricesRouteBody />
    </div>
  );
}


