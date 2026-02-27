import PageHeader from "@/components/PageHeader";

export default function SettingsPage() {
  return (
    <div className="space-y-4">
      <PageHeader
        title="Settings"
        subtitle="Keys · Defaults · Nexus integration (v3)"
        badge="BETA"
      />
      <div className="rounded-xl border border-white/10 bg-white/[0.03] p-4">
        <div className="text-sm font-semibold text-white/85">Coming in v3</div>
        <div className="mt-1 text-[12px] text-white/50">
          Customer dashboard, subscriptions, API keys, on-demand symbols/fiat and Nexus MCP controls.
        </div>
        </div>
    </div>
  );
}
