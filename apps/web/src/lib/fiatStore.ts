const KEY = "CRYPTOLINK_FIAT";

export function getFiat(): string {
  if (typeof window === "undefined") return "USD";
  const v = window.localStorage.getItem(KEY);
  return (v || "USD").toUpperCase();
}

export function setFiat(v: string) {
  window.localStorage.setItem(KEY, String(v).toUpperCase());
}
