"use client";

import { useEffect, useState } from "react";
import { getSymbols, setSymbols } from "@/lib/symbolsStore";

export default function SymbolsEditor() {
  const [value, setValue] = useState("");

  useEffect(() => {
    setValue(getSymbols().join(","));
  }, []);

  const save = () => {
    const list = value.split(",").map((s) => s.trim());
    setSymbols(list);
  };

  return (
    <section
      style={{
        marginTop: 16,
        padding: 16,
        borderRadius: 14,
        border: "1px solid rgba(255,255,255,0.08)",
        background: "rgba(255,255,255,0.03)",
        minHeight: 300,
      }}
    >
      <h2 style={{ margin: 0, fontSize: 16 }}>Symbols</h2>
      <p style={{ marginTop: 6, fontSize: 12, opacity: 0.75 }}>
        Configura los símbolos a monitorear (ej: BTC,ETH,SOL)
      </p>

      <input
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder="BTC,ETH,SOL"
        style={{
          width: "100%",
          marginTop: 8,
          padding: "10px 12px",
          borderRadius: 10,
          border: "1px solid rgba(255,255,255,0.15)",
          background: "transparent",
          color: "inherit",
        }}
      />

      <button
        onClick={save}
        style={{
          marginTop: 10,
          padding: "8px 14px",
          borderRadius: 10,
          border: "1px solid rgba(255,255,255,0.15)",
          background: "rgba(255,255,255,0.06)",
          cursor: "pointer",
          fontWeight: 700,
        }}
      >
        Guardar
      </button>
    </section>
  );
}
