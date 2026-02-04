"use client";

import { useEffect, useState } from "react";
import { getApiKey } from "@/lib/apiKey";
import { fetchPrice } from "@/lib/cryptoLink";

type Row = {
  symbol: string;
  fiat: string;
  price?: number;
  raw?: any;
};

export default function PricesPanel() {
  const [rows, setRows] = useState<Row[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  async function load() {
    try {
      setLoading(true);
      setError(null);

      const apiKey = getApiKey();
      if (!apiKey) {
        setRows([]);
        setError("Falta API Key (pégala arriba).");
        return;
      }

      const fiat = "USD";

      const [btc, eth] = await Promise.all([
        fetchPrice("BTC", fiat, apiKey),
        fetchPrice("ETH", fiat, apiKey),
      ]);

      // Ajusta aquí si tu JSON trae el precio con otro nombre
      const btcPrice = btc?.price ?? btc?.data?.price ?? btc?.value ?? btc?.rate;
      const ethPrice = eth?.price ?? eth?.data?.price ?? eth?.value ?? eth?.rate;

      setRows([
        { symbol: "BTC", fiat, price: typeof btcPrice === "number" ? btcPrice : undefined, raw: btc },
        { symbol: "ETH", fiat, price: typeof ethPrice === "number" ? ethPrice : undefined, raw: eth },
      ]);
    } catch (e: any) {
      setError(e?.message ?? "Error cargando precios");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
    const id = setInterval(load, 5000);
    return () => clearInterval(id);
  }, []);

  return (
    <section style={{ marginTop: 16, padding: 16, border: "1px solid #ddd", borderRadius: 12 }}>
      <h2 style={{ margin: 0 }}>Prices</h2>
      <p style={{ marginTop: 8, opacity: 0.8 }}>
        Endpoint: <code>/v1/price</code> — fiat: <code>USD</code>
      </p>

      {loading && <p>Cargando…</p>}
      {error && <p style={{ color: "red" }}>Error: {error}</p>}

      {!loading && !error && (
        <ul>
          {rows.map((r) => (
            <li key={r.symbol}>
              <b>{r.symbol}</b> → {typeof r.price === "number" ? `$${r.price}` : <i>No pude leer el precio del JSON</i>}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
