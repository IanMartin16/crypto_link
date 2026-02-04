import { NextRequest, NextResponse } from "next/server";

export const runtime = "nodejs";

export async function GET(req: NextRequest) {
  try {
    const apiKey = req.headers.get("x-api-key") || "";
    if (!apiKey) {
      return NextResponse.json({ ok: false, error: "Missing x-api-key" }, { status: 401 });
    }

    const { searchParams } = new URL(req.url);
    const symbol = searchParams.get("symbol") || "BTC";
    const fiat = searchParams.get("fiat") || "USD";

    const base = process.env.CRYPTOLINK_API_BASE || "http://localhost:8080";
    const url = `${base}/v1/price?symbol=${encodeURIComponent(symbol)}&fiat=${encodeURIComponent(fiat)}`;

    const res = await fetch(url, {
      headers: { "x-api-key": apiKey },
      cache: "no-store"
    });

    const text = await res.text();
    return new NextResponse(text, {
      status: res.status,
      headers: { "content-type": res.headers.get("content-type") || "application/json" }
    });
  } catch (e: any) {
    return NextResponse.json({ ok: false, error: e?.message ?? "proxy_error" }, { status: 500 });
  }
}
