CREATE TABLE IF NOT EXISTS cryptolink_api_keys (
  api_key TEXT PRIMARY KEY,
  plan    TEXT NOT NULL,
  status  TEXT NOT NULL,
  expires_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
