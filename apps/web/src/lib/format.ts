export function formatPrice(value: number, fiat: string) {
  try {
    return new Intl.NumberFormat("en-US", {
      style: "currency",
      currency: fiat,
      maximumFractionDigits: fiat === "JPY" ? 0 : 2,
    }).format(value);
  } catch {
    return `$${value}`;
  }
}

export function shortTs(v: string) {
  if (!v) return "-";
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return v.slice(0, 10);
  return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
}

export function shortTime(v?: string) {
  if (!v) return "—";
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return "—";
  return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
}

export function formatMoney(value: number, fiat: string) {
  try {
    return new Intl.NumberFormat("en-US", {
      style: "currency",
      currency: fiat || "USD",
      maximumFractionDigits: fiat === "JPY" ? 0 : 2,
    }).format(value);
  } catch {
    return `$${value}`;
  }
}
