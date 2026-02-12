import { NextRequest, NextResponse } from "next/server";

export const runtime = "nodejs";

export async function GET(req: NextRequest) {
  try {
    const base = process.env.CRYPTOLINK_API_BASE || "http://localhost:8080";
    const url = `${base}/v1/fiats`;

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
