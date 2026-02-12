import { NextRequest, NextResponse } from "next/server";

export const runtime = "nodejs";

type CacheEntry = {
  ts: number;          // Date.now()
  status: number;
  body: string;
  contentType: string;
};

const TTL_MS = 5000;

// cache en memoria del proceso
const cache = new Map<string, CacheEntry>();

function cacheKey(symbol: string, fiat: string, apiKey: string) {
  // Cache por usuario/key (para no mezclar planes/limits)
  // Si quieres cache global (más agresivo), quita apiKey del key.
  return `${apiKey}::${symbol}::${fiat}`;
}

export async function GET(req: NextRequest) {
  try {
    const apiKey = req.headers.get("x-api-key")?.trim() || "";
    if (!apiKey) {
      return NextResponse.json({ ok: false, error: "Missing x-api-key" }, { status: 401 });
    }

    const { searchParams } = new URL(req.url);
    const symbol = (searchParams.get("symbol") || "BTC").toUpperCase();
    const fiat = (searchParams.get("fiat") || "USD").toUpperCase();

    const key = cacheKey(symbol, fiat, apiKey);
    const now = Date.now();

    const hit = cache.get(key);
    if (hit && now - hit.ts <= TTL_MS) {
      return new NextResponse(hit.body, {
        status: hit.status,
        headers: {
          "content-type": hit.contentType,
          "x-bff-cache": "HIT",
          "x-bff-cache-age-ms": String(now - hit.ts),
        },
      });
    }

    const base = process.env.CRYPTOLINK_API_BASE || "http://localhost:8080";
    const url = `${base}/v1/price?symbol=${encodeURIComponent(symbol)}&fiat=${encodeURIComponent(fiat)}`;

    const res = await fetch(url, {
      headers: { "x-api-key": apiKey },
      cache: "no-store",
    });

    const body = await res.text();
    const contentType = res.headers.get("content-type") || "application/json";

    cache.set(key, { ts: now, status: res.status, body, contentType });

    return new NextResponse(body, {
      status: res.status,
      headers: {
        "content-type": contentType,
        "x-bff-cache": "MISS",
      },
    });
  } catch (e: any) {
    return NextResponse.json({ ok: false, error: e?.message ?? "proxy_error" }, { status: 500 });
  }
}
