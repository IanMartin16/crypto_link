export type Health = {
  ok: boolean;
  lastOkAt?: string;
  lastErr?: string;
};

export const HEALTH_OK: Health = { ok: true };
export const HEALTH_ERR: Health = { ok: false, lastErr: "unknown" };
