"use client";

export function Skeleton({
  w = "100%",
  h = 12,
  r = 10,
  style = {},
}: {
  w?: number | string;
  h?: number;
  r?: number;
  style?: React.CSSProperties;
}) {
  return (
    <span
      style={{
        display: "inline-block",
        width: w,
        height: h,
        borderRadius: r,
        background:
          "linear-gradient(90deg, rgba(255,255,255,0.06), rgba(255,255,255,0.14), rgba(255,255,255,0.06))",
        backgroundSize: "200% 100%",
        animation: "cl-skeleton 1.1s ease-in-out infinite",
        ...style,
      }}
    >
      <style>{`
        @keyframes cl-skeleton {
          0% { background-position: 200% 0; }
          100% { background-position: -200% 0; }
        }
      `}</style>
    </span>
  );
}
