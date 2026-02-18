export default function RefreshDot({ on }: { on: boolean }) {
  return (
    <span
      title={on ? "Actualizando" : "Idle"}
      style={{
        width: 8,
        height: 8,
        borderRadius: 999,
        display: "inline-block",
        background: on ? "rgba(255,159,67,0.95)" : "rgba(255,255,255,0.18)",
        boxShadow: on ? "0 0 14px rgba(255,159,67,0.35)" : "none",
        animation: on ? "pulseDot 1.1s ease-in-out infinite" : "none",
        flex: "0 0 auto",
      }}
    />
  );
}
