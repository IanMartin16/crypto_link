"use client";

import { useEffect, useRef, useState } from "react";

const history: Record<string, number[]> = {};

export function pushPriceHistory(symbol: string, price: number, max = 600) {
  const key = symbol.toUpperCase();
  const arr = history[key] ?? (history[key] = []);
  arr.push(price);
  if (arr.length > max) arr.splice(0, arr.length - max);
}

export function getPriceHistory(symbol: string): number[] {
  return history[symbol.toUpperCase()] ?? [];
}

export function usePriceHistory(symbol: string, price?: number, maxPoints = 600) {
  const [values, setValues] = useState<number[]>([]);
  const last = useRef<number | undefined>(undefined);

  useEffect(() => {
    setValues([]);
    last.current = undefined;
  }, [symbol]);

  useEffect(() => {
    if (typeof price !== "number") return;
    if (last.current === price) return;

    last.current = price;

    // ✅ alimenta global (para overlays)
    pushPriceHistory(symbol, price, maxPoints);

    setValues((prev) => {
      const next = [...prev, price];
      if (next.length > maxPoints) next.splice(0, next.length - maxPoints);
      return next;
    });
  }, [price, maxPoints, symbol]);

  return values;
}