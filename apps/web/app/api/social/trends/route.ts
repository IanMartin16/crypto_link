import { NextRequest, NextResponse } from "next/server";
export const runtime = "nodejs";

function parseSymbols(raw: string | null): string[] {
  const arr = (raw || "BTC,ETH")
    .split(",")
    .map((x) => x.trim().toUpperCase())
    .filter(Boolean)
    .slice(0, 60);

  return arr.length ? arr : ["BTC", "ETH"];
}

async function proxyToSocialLink(symbolsCsv: string) {
  const base = process.env.SOCIAL_LINK_BASE_URL;
  if (!base) return null;

  const url = `${base}/internal/v1/trends?symbols=${encodeURIComponent(symbolsCsv)}`;
  const res = await fetch(url, { cache: "no-store" });

  // Intentamos normalizar respuesta para que el UI siempre reciba {items:[]}
  const contentType = res.headers.get("content-type") || "application/json";
  const text = await res.text();

  // Si ya viene en formato correcto, lo devolvemos tal cual
  // Si no, lo devolvemos como payload “proxy”
  try {
    const json = JSON.parse(text);
    const items = json.items ?? json.data?.items ?? json.data ?? json.trends ?? [];
    return NextResponse.json(
      {
        ok: res.ok,
        source: "social_link",
        updatedAt: json.updatedAt ?? new Date().toISOString(),
        items,
        raw: json, // opcional, quítalo si no quieres
      },
      { status: res.status }
    );
  } catch {
    // No era JSON parseable
    return new NextResponse(text, { status: res.status, headers: { "content-type": contentType } });
  }
}

// ✅ memoria simple (vive mientras el server process vive)
const lastPriceBySymbol = new Map<string, number>();

function toDerivedTrendsFromPrices(payload: any) {
  const list = payload?.data ?? payload?.rows ?? payload?.items ?? [];
  const rows = list.map((x: any) => {
    const symbol = String(x?.symbol ?? x?.data?.symbol ?? "").toUpperCase();
    const d = x?.data ?? x ?? {};
    const price = typeof d?.price === "number" ? d.price : undefined;
    const ts = d?.ts ?? d?.updatedAt ?? payload?.ts ?? new Date().toISOString();
    const source = d?.source ?? payload?.source ?? "cryptolink";
    const ok = x?.ok ?? d?.ok ?? true;

    let pct: number | undefined = typeof d?.pct === "number" ? d.pct : undefined;

    // ✅ fallback: si no viene pct, lo calculamos con el último precio guardado
    if (pct === undefined && typeof price === "number") {
      const last = lastPriceBySymbol.get(symbol);
      if (typeof last === "number" && last !== 0 && last !== price) {
        pct = ((price - last) / last) * 100;
      }
      // guardamos siempre el último
      lastPriceBySymbol.set(symbol, price);
    }

    return { symbol, price, pct, ok, ts, source };
  });

  // orden: primero los que tengan pct, luego el resto
  const sorted = [...rows].sort((a, b) => {
    const ao = typeof a.pct === "number" ? 0 : 1;
    const bo = typeof b.pct === "number" ? 0 : 1;
    if (ao !== bo) return ao - bo;

    const ap = Math.abs(a.pct ?? 0);
    const bp = Math.abs(b.pct ?? 0);
    if (bp !== ap) return bp - ap;

    return (a.symbol || "").localeCompare(b.symbol || "");
  });

  return sorted.slice(0, 20).map((r, i) => ({
    rank: i + 1,
    symbol: r.symbol,
    score: typeof r.pct === "number" ? r.pct : 0,
    trend: (r.pct ?? 0) > 0 ? "UP" : (r.pct ?? 0) < 0 ? "DOWN" : "FLAT",
    momentum: (r.pct ?? 0) > 0 ? "UP" : (r.pct ?? 0) < 0 ? "DOWN" : "FLAT",
    ts: r.ts,
    source: r.source,
  }));
}

async function derivedFallback(req: NextRequest, symbols: string[]) {
  const { searchParams } = new URL(req.url);
  const fiat = (searchParams.get("fiat") || "USD").toUpperCase();

  const apiKey = process.env.CRYPTOLINK_DEMO_KEY;
  if (!apiKey) {
    return NextResponse.json({
      ok: true,
      source: "derived",
      updatedAt: new Date().toISOString(),
      items: [],
      note: "CRYPTOLINK_DEMO_KEY not configured on server.",
    });
  }

  const u = new URL("/api/cryptolink/prices", req.nextUrl.origin);
  u.searchParams.set("symbols", symbols.join(","));
  u.searchParams.set("fiat", fiat);

  const res = await fetch(u.toString(), {
    cache: "no-store",
    headers: { "x-api-key": apiKey },
  });

  const data = await res.json();
  const items = toDerivedTrendsFromPrices(data);

  return NextResponse.json({
    ok: true,
    source: "derived",
    updatedAt: new Date().toISOString(),
    items,
    note: "Derived from CryptoLink prices (no external social providers yet).",
  });
}

export async function GET(req: NextRequest) {
  try {
    const { searchParams } = new URL(req.url);
    const symbols = parseSymbols(searchParams.get("symbols"));
    const symbolsCsv = symbols.join(",");

    // 1) intenta proxy al servicio Social_Link real
    const proxied = await proxyToSocialLink(symbolsCsv);
    if (proxied) return proxied;

    // 2) fallback derivado
    return await derivedFallback(req, symbols);
  } catch (e: any) {
    return NextResponse.json({ ok: false, error: e?.message ?? "trends_error" }, { status: 500 });
  }
}