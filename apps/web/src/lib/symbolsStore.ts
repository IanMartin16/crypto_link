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
  const clean = Array.from(
    new Set((symbols || []).map((s) => String(s).toUpperCase().trim()).filter(Boolean))
  ).slice(0, 20);

  try {
    localStorage.setItem(KEY, JSON.stringify(clean));
  } catch {}

  // ✅ defer event (evita setState during render en listeners)
  queueMicrotask(() => {
    try {
      window.dispatchEvent(new CustomEvent("cryptolink:symbols"));
    } catch {}
  });
}