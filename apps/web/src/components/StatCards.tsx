"use client";

import { useMemo } from "react";
import HeroPriceCard from "@/components/HeroPriceCard";

type Row = {
  symbol: string;
  fiat: string;
  cache?: string;
  price?: number;
  ok?: boolean;
};

export default function StatCards({ rows }: { rows: Row[] }) {
  const map = useMemo(() => new Map(rows.map((r) => [r.symbol, r])), [rows]);

  const btc = map.get("BTC");
  const eth = map.get("ETH");

  // Si BTC no existe todavía, agarra fiat del primer row para evitar fallback raro.
  const fiat = btc?.fiat ?? rows[0]?.fiat ?? "USD";

  return (
    <div style={{ display: "grid", gridTemplateColumns: "repeat(2, minmax(0, 1fr))", gap: 12 }}>
      <HeroPriceCard symbol="BTC" fiat={fiat} price={btc?.price} cache={btc?.cache} />
      <HeroPriceCard symbol="ETH" fiat={fiat} price={eth?.price} cache={eth?.cache} />
    </div>
  );
}
