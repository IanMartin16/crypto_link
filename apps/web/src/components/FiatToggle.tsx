"use client";

import { useEffect, useState } from "react";
import { getFiat, setFiat } from "@/lib/fiatStore";

type FiatsResponse =
  | string[]
  | { data?: string[]; fiats?: string[]; ok?: boolean };

function normalizeFiats(r: FiatsResponse): string[] {
  if (Array.isArray(r)) return r.map((x) => String(x).toUpperCase());

  const list = (r as any)?.fiats ?? (r as any)?.data;
  if (Array.isArray(list)) return list.map((x: any) => String(x).toUpperCase()).filter(Boolean);

  return ["USD"];
}


export default function FiatToggle() {
  const [fiats, setFiats] = useState<string[]>([]);
  const [current, setCurrent] = useState<string>("USD");
  const [err, setErr] = useState<string | null>(null);

  async function loadFiats() {
    try {
      setErr(null);
      const res = await fetch("/api/cryptolink/fiats", { cache: "no-store" });
      if (!res.ok) throw new Error(`fiats HTTP ${res.status}`);
      const json = (await res.json()) as FiatsResponse;

      const list = normalizeFiats(json);
      setFiats(list);

      // valida el seleccionado actual
      const saved = getFiat(); // "USD" | "MXN" (tu store actual)
      const savedStr = String(saved).toUpperCase();

      const next = list.includes(savedStr) ? savedStr : list[0] || "USD";
      setCurrent(next);
      setFiat(next as any); // ok aunque tu tipo sea USD|MXN, en la práctica guardamos string
      window.dispatchEvent(new CustomEvent("cryptolink:fiat"));
    } catch (e: any) {
      setErr(e?.message ?? "Error cargando fiats");
      // fallback visual
      const saved = String(getFiat()).toUpperCase();
      setFiats(["USD", "MXN"]);
      setCurrent(saved === "MXN" ? "MXN" : "USD");
    }
  }

  useEffect(() => {
    loadFiats();
  }, []);

  const pick = (v: string) => {
    setFiat(v as any);
    setCurrent(v);
    window.dispatchEvent(new CustomEvent("cryptolink:fiat"));
  };

  return (
    <section
      style={{
        marginTop: 16,
        padding: 16,
        borderRadius: 14,
        border: "1px solid rgba(255,255,255,0.08)",
        background: "rgba(255,255,255,0.03)",
      }}
    >
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", gap: 12 }}>
        <h2 style={{ margin: 0, fontSize: 16 }}>Fiat</h2>
        <button
          onClick={loadFiats}
          style={{
            padding: "6px 10px",
            borderRadius: 10,
            border: "1px solid rgba(255,255,255,0.12)",
            background: "rgba(255,255,255,0.04)",
            cursor: "pointer",
            fontWeight: 700,
            fontSize: 12,
          }}
        >
          Refresh
        </button>
      </div>

      <p style={{ marginTop: 6, fontSize: 12, opacity: 0.75 }}>
        Selecciona la moneda para precios.
      </p>
      {err && <p style={{ marginTop: 6, fontSize: 12, color: "#ff6b6b" }}>⚠ {err}</p>}

      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 8 }}>
        {fiats.map((f) => (
          <button
            key={f}
            onClick={() => pick(f)}
            style={{
              padding: "10px 14px",
              borderRadius: 10,
              border: "1px solid rgba(255,255,255,0.15)",
              background: current === f ? "rgba(255,255,255,0.10)" : "transparent",
              cursor: "pointer",
              fontWeight: current === f ? 900 : 600,
              color: "inherit",
            }}
          >
            {f}
          </button>
        ))}
      </div>
    </section>
  );
}
