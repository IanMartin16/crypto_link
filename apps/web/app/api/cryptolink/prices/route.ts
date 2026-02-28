import { NextRequest, NextResponse } from "next/server";

export const runtime = "nodejs";

// --- cache en memoria (por proceso) ---
type CacheEntry = { ts: number; body: any; status: number };
const TTL_MS = 5000;
const cache = new Map<string, CacheEntry>();

function hashKey(apiKey: string) {
  let h = 0;
  for (let i = 0; i < apiKey.length; i++) h = (h * 31 + apiKey.charCodeAt(i)) | 0;
  return String(h >>> 0);
}

function makeKey(base: string, symbol: string, fiat: string, apiKey: string) {
  return `${base}|${symbol}|${fiat}|k:${hashKey(apiKey)}`;
}

async function fetchOne(base: string, symbol: string, fiat: string, apiKey: string) {
  const key = makeKey(base, symbol, fiat, apiKey);
  const now = Date.now();

  const hit = cache.get(key);
  if (hit && now - hit.ts < TTL_MS) {
    return { symbol, ok: hit.status >= 200 && hit.status < 300, data: hit.body, cache: "HIT" as const };
  }

  const url = `${base}/v1/price?symbol=${encodeURIComponent(symbol)}&fiat=${encodeURIComponent(fiat)}`;
  const res = await fetch(url, {
    headers: { "x-api-key": apiKey },
    cache: "no-store",
  });

  // asumimos JSON
  let body: any;
  const text = await res.text();
  try {
    body = JSON.parse(text);
  } catch {
    body = { raw: text };
  }

  cache.set(key, { ts: now, body, status: res.status });

  return { symbol, ok: res.ok, data: body, cache: "MISS" as const };
}

export async function GET(req: NextRequest) {
  try {
    const headerKey = req.headers.get("x-api-key") || "";
    const apiKey = headerKey || process.env.CRYPTOLINK_DEMO_KEY || "";
    if (!apiKey) 
      return NextResponse.json(
    { ok: false, error: "Missing x-api-key" }, { status: 401 

    }
  );

    const { searchParams } = new URL(req.url);
    const symbolsParam = searchParams.get("symbols") || "BTC,ETH";
    const fiat = (searchParams.get("fiat") || "USD").toUpperCase();

    const symbols = symbolsParam
      .split(",")
      .map((s) => s.trim().toUpperCase())
      .filter(Boolean)
      .slice(0, 50); // seguridad

    const base = process.env.CRYPTOLINK_API_BASE || "http://localhost:8080";

    const results = await Promise.all(symbols.map((sym) => fetchOne(base, sym, fiat, apiKey)));

    // header de cache global (si todos son HIT, genial)
    const allHit = results.every((r) => r.cache === "HIT");

    return NextResponse.json(
      {
        ok: true,
        ts: new Date().toISOString(),
        fiat,
        data: results,
      },
      {
        headers: { "x-bff-cache": allHit ? "HIT" : "MIXED" },
      }
    );
  } catch (e: any) {
    return NextResponse.json({ ok: false, error: e?.message ?? "proxy_error" }, { status: 500 });
  }
}
