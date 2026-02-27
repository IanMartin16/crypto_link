const KEY = "cryptolink:chart_watch";

function clean(sym: string) {
  return String(sym).toUpperCase().trim();
}

export function getChartWatch(): string[] {
  try {
    const raw = localStorage.getItem(KEY);
    const arr = raw ? JSON.parse(raw) : [];
    if (!Array.isArray(arr)) return [];
    return arr.map(clean).filter(Boolean).slice(0, 5);
  } catch {
    return [];
  }
}

export function setChartWatch(list: string[]) {
  const next = list.map(clean).filter(Boolean);
  // unique + max 5
  const uniq: string[] = [];
  for (const s of next) if (!uniq.includes(s)) uniq.push(s);
  localStorage.setItem(KEY, JSON.stringify(uniq.slice(0, 5)));
}

export function addToChartWatch(symbol: string) {
  const s = clean(symbol);
  const cur = getChartWatch();
  if (cur.includes(s)) return cur;
  const next = [...cur, s].slice(0, 5);
  setChartWatch(next);
  return next;
}

export function removeFromChartWatch(symbol: string) {
  const s = clean(symbol);
  const next = getChartWatch().filter((x) => x !== s);
  setChartWatch(next);
  return next;
}

export function toggleChartWatch(symbol: string) {
  const s = clean(symbol);
  const cur = getChartWatch();
  if (cur.includes(s)) return removeFromChartWatch(s);
  return addToChartWatch(s);
}