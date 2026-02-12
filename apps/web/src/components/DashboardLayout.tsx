import { ReactNode } from "react";

export default function DashboardLayout({ children }: { children: ReactNode }) {
  return (
    <div
      style={{
        minHeight: "100vh",
        padding: 24,
        fontFamily: "system-ui",
        background: "#0b0f14",
        color: "#e6edf3",
      }}
    >
      <div style={{ maxWidth: 1200, margin: "0 auto" }}>{children}</div>
    </div>
  );
}
