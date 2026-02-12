"use client";

import { useMemo, useEffect, useState } from "react";

function formatPrice(value: number, fiat: string) {
  try {
    return new Intl.NumberFormat("en-US", {
      style: "currency",
      currency: fiat,
      maximumFractionDigits: fiat === "JPY" ? 0 : 2,
    }).format(value);
  } catch {
    return `$${value}`;
  }
}

export default function HeroPriceCard({
  symbol,
  fiat,
  price,
  cache, // ✅ IMPORTANTE
}: {
  symbol: string;
  fiat: string;
  price?: number;
  cache?: string; // ✅ IMPORTANTE
}) {
  const priceNode = useMemo(() => {
    if (typeof price !== "number") return "—";
    return formatPrice(price, fiat);
  }, [price, fiat]);

  useEffect(() => {
  console.log("[HeroPriceCard mount]", symbol);
  return () => console.log("[HeroPriceCard unmount]", symbol);
}, [symbol]);

  // ✅ Label correcto: HIT = cache, MISS = live
  const cacheLabel = cache === "HIT" ? "CACHE" : "LIVE";

  const [flash, setFlash] = useState<"up" | "down" | null>(null);
  const [pulse, setPulse] = useState(false);

  useEffect(() => {
    if (typeof price !== "number") return;

    const key = `__last_${symbol}_${fiat}`;
    const last = (window as any)[key] as number | undefined;
    (window as any)[key] = price;

    if (typeof last === "number") {
      if (price > last) setFlash("up");
      else if (price < last) setFlash("down");
      else setFlash(null);

      setPulse(true);

      const tPulse = setTimeout(() => setPulse(false), 320);
      const tFlash = setTimeout(() => setFlash(null), 450);

      return () => {
        clearTimeout(tPulse);
        clearTimeout(tFlash);
      };
    }
  }, [price, fiat, symbol]);

  const outline =
    flash
      ? `1px solid ${
          flash === "up" ? "rgba(46,229,157,0.55)" : "rgba(255,107,107,0.55)"
        }`
      : "none";

  const glow = flash === "up" ? "rgba(46,229,157,0.22)" : "rgba(255,107,107,0.22)";

  const badgeStyle =
    cacheLabel === "CACHE"
      ? {
          border: "1px solid rgba(46,229,157,0.22)",
          background: "rgba(46,229,157,0.10)",
          color: "#2ee59d",
        }
      : {
          border: "1px solid rgba(255,159,67,0.20)",
          background: "rgba(255,159,67,0.10)",
          color: "#ffb86b",
        };

  return (
    <div
      style={{
        padding: 18,
        borderRadius: 16,
        background: "rgba(255,255,255,0.03)",
        outline,
        boxShadow: flash
          ? `0 0 0 1px rgba(255,159,67,0.06), 0 0 18px ${glow}`
          : "0 0 0 1px rgba(255,159,67,0.06), 0 10px 30px rgba(0,0,0,0.25)",
        transform: pulse ? "scale(1.01)" : "scale(1)",
        transition: "transform 180ms ease, box-shadow 180ms ease, outline 180ms ease",
      }}
    >
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <span
            style={{
              padding: "4px 10px",
              borderRadius: 999,
              border: "1px solid rgba(255,255,255,0.10)",
              background: "rgba(255,255,255,0.03)",
              fontWeight: 900,
              letterSpacing: 0.2,
            }}
          >
            {symbol}
          </span>
          <span style={{ fontSize: 12, opacity: 0.75 }}>Actualiza cada 5s · batch BFF</span>
        </div>

        <span style={{ fontSize: 12, opacity: 0.7 }}>{fiat}</span>
      </div>

      <div
        style={{
          marginTop: 14,
          fontSize: 38,
          fontWeight: 950,
          color: "#ffb86b",
          textShadow: "0 0 12px rgba(255,159,67,0.18)",
          letterSpacing: 0.2,
          lineHeight: 1.05,
          minHeight: 42,
          display: "flex",
          alignItems: "center",
        }}
      >
        {priceNode}
      </div>

      <div style={{ marginTop: 10, display: "flex", gap: 10 }}>
        <span
          style={{
            padding: "2px 10px",
            borderRadius: 999,
            fontSize: 12,
            fontWeight: 800,
            ...badgeStyle,
          }}
        >
          {cacheLabel}
        </span>
      </div>
    </div>
  );
}
