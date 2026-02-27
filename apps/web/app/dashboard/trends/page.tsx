"use client";

import { useState } from "react";
import TrendsRouteBody from "@/components/TrendsRouteBody";
import TrendsTable from "@/components/TrendsTable";
import StatusBar from "@/components/StatusBar";
import type { Health } from "@/lib/health";
import { HEALTH_OK } from "@/lib/health";
import PageHeader from "@/components/PageHeader";

export default function TrendsPage() {
  const [trendsHealth, setTrendsHealth] = useState<Health>(HEALTH_OK);
  return (
    <div>
      <PageHeader
        title="Trends"
        subtitle="Social_link · 10s refresh"
        health={trendsHealth}
        badge="DERIVED"
      />

      <div style={{ marginTop: 12, display: "grid", gridTemplateColumns: "repeat(2, minmax(0, 1fr))", gap: 12 }}>
      </div>
      <TrendsRouteBody />
    </div>
  );
}
