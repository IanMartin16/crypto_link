"use client";

import type { Health } from "@/lib/health";

export default function PageHeader({
  title,
  subtitle,
  health,
  badge = "LIVE",
}: {
  title: string;
  subtitle?: string;
  health?: Health;
  badge?: "LIVE" | "DERIVED" | "BETA" | "—";
}) {
  const tone =
    health?.ok === true
      ? "border-emerald-400/25 bg-emerald-400/10 text-emerald-200"
      : health?.ok === false
      ? "border-rose-400/25 bg-rose-400/10 text-rose-200"
      : "border-white/10 bg-white/[0.03] text-white/70";

  const label = health?.ok === false ? "DEGRADED" : badge;

  return (
    <div className="flex flex-wrap items-baseline justify-between gap-3">
      <div>
        <h1 className="m-0 text-xl font-semibold text-white/90">{title}</h1>
        {subtitle ? (
          <div className="mt-1 text-[12px] text-white/50">{subtitle}</div>
        ) : null}
      </div>

      <span
        className={[
          "inline-flex items-center rounded-md border px-2 py-0.5 text-[11px] font-semibold",
          tone,
        ].join(" ")}
      >
        {label}
      </span>
    </div>
  );
}