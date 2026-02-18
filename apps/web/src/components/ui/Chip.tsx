import { UI } from "@/lib/ui";

export default function Chip({ children }: { children: React.ReactNode }) {
  return (
    <span
      style={{
        padding: "4px 10px",
        borderRadius: 999,
        border: `1px solid ${UI.border}`,
        background: "rgba(255,255,255,0.03)",
        fontSize: 12,
        fontWeight: 900,
        opacity: 0.9,
        whiteSpace: "nowrap",
        maxWidth: "100%",
        overflow: "hidden",
        textOverflow: "ellipsis",
      }}
    >
      {children}
    </span>
  );
}
