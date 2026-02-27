"use client";

import React, { useMemo } from "react";

export default function Sparkline({
  values,
  w = 88,
  h = 22,
  stroke,
  fill,
}: {
  values: number[];
  w?: number;
  h?: number;
  stroke: string;
  fill: string;
}) {
  if (!values || values.length < 2) {
    return (
      <svg width={w} height={h}>
        <line
          x1={0}
          y1={h / 2}
          x2={w}
          y2={h / 2}
          stroke="rgba(255,255,255,0.15)"
          strokeWidth={1}
        />
      </svg>
    );
  }

  const max = Math.max(...values);
  const min = Math.min(...values);
  const range = max - min || 1;

  const points = values.map((v, i) => {
    const x = (i / (values.length - 1)) * w;
    const y = h - ((v - min) / range) * h;
    return `${x},${y}`;
  });

  const area = `M0,${h} L${points.join(" L")} L${w},${h} Z`;

  return (
    <svg width={w} height={h}>
      <path d={area} fill={fill} />
      <polyline
        points={points.join(" ")}
        fill="none"
        stroke={stroke}
        strokeWidth={1.6}
        style={{
          filter: `drop-shadow(0 0 6px ${stroke}55)`,
          transition: "all 220ms ease",
        }}
      />
    </svg>
  );
}
