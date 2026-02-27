"use client";

import { getCryptoIconUrl } from "@/lib/cryptoIcons";

function initials(sym: string) {
  return (sym || "—").slice(0, 3).toUpperCase();
}

export default function SymbolCell({
  symbol,
  fiat,
}: {
  symbol: string;
  fiat?: string;
}) {
  const iconUrl = getCryptoIconUrl(symbol);

  return (
    <div className="flex items-center gap-2">
      {iconUrl ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={iconUrl}
          alt={symbol}
          className="h-5 w-5 rounded-full"
          loading="lazy"
        />
      ) : (
        <div className="flex h-5 w-5 items-center justify-center rounded-full bg-white/10 text-[10px] font-semibold text-white/70">
          {initials(symbol)}
        </div>
      )}

      <div className="min-w-0 leading-tight">
        <div className="truncate text-sm font-semibold text-white/90">
          {symbol}
        </div>
        {fiat ? <div className="text-[11px] text-white/45">{fiat}</div> : null}
      </div>
    </div>
  );
}