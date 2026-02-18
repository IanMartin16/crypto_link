"use client";

import { useState } from "react";
import PricesPanel from "@/components/PricesPanel";
import StatCards from "@/components/StatCards";
import StatusBar from "@/components/StatusBar";

export default function PricesRouteBody() {
  const [rows, setRows] = useState<any[]>([]);
  const [pricesHealth, setPricesHealth] = useState<any>(undefined);

  return (
    <>
      <div style={{ marginTop: 12 }}>
        <StatusBar prices={pricesHealth} />
      </div>

      <div style={{ marginTop: 12 }}>
        <StatCards rows={rows} />
      </div>

      <div style={{ marginTop: 12 }}>
        <PricesPanel onRows={setRows} onHealth={setPricesHealth} />
      </div>
    </>
  );
}
