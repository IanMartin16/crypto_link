"use client";

import { useEffect, useState } from "react";
import { clearApiKey, getApiKey, setApiKey } from "@/lib/apiKey";
import { UI } from "@/lib/ui";


export default function ApiKeyBar() {
  const [value, setValue] = useState("");

  useEffect(() => {
    const existing = getApiKey();
    if (existing) setValue(existing);
  }, []);

  return (
    <section 
      style={{ 
        marginTop: 16, 
        padding: 16, 
        border: `1px solid ${UI.border}`, 
        borderRadius: 14, 
        background: UI.panel
        }}>
      <h2 style={{ margin: 0 }}>API Key</h2>
      <p style={{ marginTop: 8, opacity: 0.8 }}>
        Pega tu key para que el dashboard pueda consumir <code>/v1/prices</code>.
      </p>

      <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
        <input
          value={value}
          onChange={(e) => setValue(e.target.value)}
          placeholder="ck_live_... o la key que uses"
          style={{ flex: 1, padding: 10, borderRadius: 10, border: "1px solid #ccc" }}
        />
        <button
          onClick={() => setApiKey(value)}
          style={{ padding: "10px 14px", borderRadius: 10, border: "1px solid #ccc", cursor: "pointer" }}
        >
          Guardar
        </button>
        <button
          onClick={() => {
            clearApiKey();
            setValue("");
          }}
          style={{ padding: "10px 14px", borderRadius: 10, border: "1px solid #ccc", cursor: "pointer" }}
        >
          Limpiar
        </button>
      </div>
    </section>
  );
}
