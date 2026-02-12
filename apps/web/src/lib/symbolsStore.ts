const KEY = "CRYPTOLINK_SYMBOLS";

export function getSymbols(): string[] {
  if (typeof window === "undefined") return ["BTC", "ETH"];
  const raw = window.localStorage.getItem(KEY);
  if (!raw) return ["BTC", "ETH"];
  try {
    const arr = JSON.parse(raw);
    return Array.isArray(arr) && arr.length ? arr : ["BTC", "ETH"];
  } catch {
    return ["BTC", "ETH"];
  }
}

export function setSymbols(symbols: string[]) {
  const clean = symbols
    .map((s) => s.trim().toUpperCase())
    .filter(Boolean)
    .slice(0, 20); // límite razonable
  window.localStorage.setItem(KEY, JSON.stringify(clean));
  window.dispatchEvent(new CustomEvent("cryptolink:symbols"));
}
