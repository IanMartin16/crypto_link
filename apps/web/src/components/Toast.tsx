"use client";

import { useEffect, useState } from "react";
import { UI } from "@/lib/ui";

type ToastState = { msg: string; tone?: "ok" | "warn" | "err" } | null;

export default function Toast({
  toast,
  onClear,
  ms = 1600,
}: {
  toast: ToastState;
  onClear: () => void;
  ms?: number;
}) {
  const [show, setShow] = useState(false);

  useEffect(() => {
    if (!toast) return;
    setShow(true);

    const t1 = setTimeout(() => setShow(false), ms);
    const t2 = setTimeout(() => onClear(), ms + 220);
    return () => {
      clearTimeout(t1);
      clearTimeout(t2);
    };
  }, [toast, ms, onClear]);

  if (!toast) return null;

  const tone = toast.tone ?? "ok";
  const color =
    tone === "ok" ? UI.green : tone === "warn" ? UI.orangeSoft : UI.red;

  return (
    <div
      style={{
        position: "fixed",
        left: "50%",
        bottom: 18,
        transform: `translateX(-50%) ${show ? "translateY(0)" : "translateY(10px)"}`,
        opacity: show ? 1 : 0,
        transition: "all 180ms ease",
        zIndex: 9999,
        pointerEvents: "none",
      }}
    >
      <div
        style={{
          padding: "10px 12px",
          borderRadius: 999,
          border: `1px solid ${UI.border}`,
          background: "rgba(10,14,20,0.92)",
          boxShadow: "0 10px 30px rgba(0,0,0,0.35)",
          color: "#e6edf3",
          fontSize: 12,
          fontWeight: 800,
          display: "flex",
          gap: 10,
          alignItems: "center",
          maxWidth: 420,
        }}
      >
        <span
          style={{
            width: 8,
            height: 8,
            borderRadius: 99,
            background: color,
            boxShadow: `0 0 12px ${color}`,
            flex: "0 0 auto",
          }}
        />
        <span style={{ opacity: 0.95, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
          {toast.msg}
        </span>
      </div>
    </div>
  );
}
