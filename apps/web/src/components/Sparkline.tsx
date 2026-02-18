"use client";

import React, { useMemo } from "react";

export default function Sparkline({
  values,
  w = 90,
  h = 24,
  stroke = "rgba(255,159,67,0.85)",
  fill = "rgba(255,159,67,0.10)",
}: {
  values: number[];
  w?: number;
  h?: number;
  stroke?: string;
  fill?: string;
}) {
  const pts = useMemo(() => {
    if (!values?.length) return "";
    const min = Math.min(...values);
    const max = Math.max(...values);
    const span = Math.max(1e-9, max - min);

    return values
      .map((v, i) => {
        const x = (i / Math.max(1, values.length - 1)) * (w - 2) + 1;
        const y = h - 1 - ((v - min) / span) * (h - 2);
        return `${x.toFixed(2)},${y.toFixed(2)}`;
      })
      .join(" ");
  }, [values, w, h]);

  const area = useMemo(() => {
    if (!pts) return "";
    return `1,${h - 1} ${pts} ${w - 1},${h - 1}`;
  }, [pts, w, h]);

  return (
    <svg width={w} height={h} viewBox={`0 0 ${w} ${h}`} style={{ display: "block" }}>
      {pts && (
        <>
          <polyline points={area} fill={fill} stroke="none" />
          <polyline points={pts} fill="none" stroke={stroke} strokeWidth={1.6} strokeLinejoin="round" strokeLinecap="round" />
        </>
      )}
    </svg>
  );
}
