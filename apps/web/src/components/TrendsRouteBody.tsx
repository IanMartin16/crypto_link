"use client";

import { useState } from "react";
import TrendsTable from "@/components/TrendsTable";
import StatusBar from "@/components/StatusBar";

export default function TrendsRouteBody() {
  const [trendsHealth, setTrendsHealth] = useState<any>(undefined);

  return (
    <>
      <div style={{ marginTop: 12 }}>
        <StatusBar trends={trendsHealth} />
      </div>

      <div style={{ marginTop: 12 }}>
        <TrendsTable onHealth={setTrendsHealth} />
      </div>
    </>
  );
}
