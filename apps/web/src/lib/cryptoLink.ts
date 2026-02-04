export type PriceItem = {
  symbol: string;
  price: number;
  change24h: number;
};

export type PriceResponse = {
  ts?: string;
  data: PriceItem[];
};

function baseUrl() {
  return process.env.NEXT_PUBLIC_CRYPTOLINK_API_BASE || "http://localhost:8080";
}

export async function fetchPrice(symbol: string, fiat: string, apiKey: string) {
  const url = `/api/cryptolink/price?symbol=${encodeURIComponent(symbol)}&fiat=${encodeURIComponent(fiat)}`;

  const res = await fetch(url, {
    cache: "no-store",
    headers: { "x-api-key": apiKey },
  });

  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(`HTTP ${res.status} - ${text}`);
  }

  return res.json();
}

