"use client";

import { useEffect, useRef, useState } from "react";

export function usePriceHistory(symbol: string, price?: number, maxPoints = 30) {
  const [values, setValues] = useState<number[]>([]);
  const last = useRef<number | undefined>(undefined);

  useEffect(() => {
    if (typeof price !== "number") return;

    // evita duplicar si llega el mismo valor
    if (last.current === price) return;
    last.current = price;

    setValues((prev) => {
      const next = [...prev, price];
      if (next.length > maxPoints) next.splice(0, next.length - maxPoints);
      return next;
    });
  }, [price, maxPoints, symbol]);

  return values;
}
