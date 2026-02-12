import { NextRequest, NextResponse } from "next/server";
export const runtime = "nodejs";

export async function GET(req: NextRequest) {
  try {
    const { searchParams } = new URL(req.url);
    const symbols = searchParams.get("symbols") || "BTC,ETH";

    const base = process.env.SOCIAL_LINK_BASE_URL || "http://localhost:8000";
    const url = `${base}/internal/v1/trends?symbols=${encodeURIComponent(symbols)}`;

    const res = await fetch(url, { cache: "no-store" });
    const text = await res.text();

    return new NextResponse(text, {
      status: res.status,
      headers: { "content-type": res.headers.get("content-type") || "application/json" },
    });
  } catch (e: any) {
    return NextResponse.json({ ok: false, error: e?.message ?? "proxy_error" }, { status: 500 });
  }
}
