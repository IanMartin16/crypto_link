"use client";

import { useState } from "react";
import PricesRouteBody from "@/components/PricesRouteBody";
import StatusBar from "@/components/StatusBar";
import PricesPanel from "@/components/PricesPanel";
import type { Health } from "@/lib/health";
import { HEALTH_OK } from "@/lib/health";
import PageHeader from "@/components/PageHeader";

export default function PricesPage() {
  const [pricesHealth, setPricesHealth] = useState<Health>(HEALTH_OK);

  return (
    <div>
        <PageHeader
          title="Prices"
          subtitle="CryptoLink · 5s refresh"
          health={pricesHealth}
          badge="LIVE"
        />

      <div style={{ marginTop: 12, display: "grid", gridTemplateColumns: "repeat(2, minmax(0, 1fr))", gap: 12 }}>
      </div>
      {/* status + hero cards + panel */}
      <PricesRouteBody />
    </div>
  );
}


