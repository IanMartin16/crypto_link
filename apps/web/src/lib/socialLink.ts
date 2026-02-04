export type TrendItem = {
  symbol: string;
  trend: "up" | "down" | "flat";
  score: number;
  reason: string;
};

export type TrendsResponse = {
  ts: string;
  data: TrendItem[];
};

function baseUrl() {
  return process.env.NEXT_PUBLIC_SOCIAL_LINK_BASE_URL || "http://localhost:8000";
}

export async function fetchTrends(symbols: string[]): Promise<TrendsResponse> {
  const qs = encodeURIComponent(symbols.join(","));
  const url = `${baseUrl()}/internal/v1/trends?symbols=${qs}`;

  const res = await fetch(url, { cache: "no-store" });
  if (!res.ok) throw new Error(`Social_link HTTP ${res.status}`);
  return (await res.json()) as TrendsResponse;
}

