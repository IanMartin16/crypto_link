"use client";

import { useState } from "react";
import TrendsTable from "@/components/TrendsTable";
import StatusBar from "@/components/StatusBar";
import TrendChartPanel from "@/components/TrendChartPanel";
import type { TrendItem } from "@/lib/types";
import type { Health } from "@/lib/health";


export default function TrendsRouteBody() {
  const [trendsHealth, setTrendsHealth] = useState<Health | undefined>(undefined);
  const [trendItems, setTrendItems] = useState<TrendItem[]>([]);

  return (
    <>
      <div style={{ marginTop: 12 }}>
        <StatusBar trends={trendsHealth} />
      </div>

      <div style={{ marginTop: 12 }}>
        <TrendChartPanel items={trendItems} maxPoints={60}/>
      </div>
      <div>
        <TrendsTable onItems={setTrendItems} onHealth={setTrendsHealth} />
      </div>
    </>
  );
}
