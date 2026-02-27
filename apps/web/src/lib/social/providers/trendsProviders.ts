import type { TrendItem } from "@/lib/types";

export type TrendDomain = "crypto" | "security" | "dev";

export type TrendsRequest = {
  domain: TrendDomain;
  symbols: string[];
  fiat?: string; // por si luego lo ocupas
};

export type TrendsResponse = {
  ok: boolean;
  domain: TrendDomain;
  source: "cryptolink" | "cryptocom" | "derived";
  updatedAt: string;
  items: TrendItem[];
  note?: string;
};

export interface TrendsProvider {
  name: TrendsResponse["source"];
  getTrends(req: TrendsRequest): Promise<TrendsResponse>;
}