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
