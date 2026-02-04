-- V5: seed / upsert de fiats y símbolos

-- FIATS (agrega los que quieras)
insert into cryptolink_fiats (code, active)
values
  ('USD', true),
  ('MXN', true),
  ('EUR', true),
  ('GBP', true),
  ('JPY', true)
on conflict (code) do update
set active = excluded.active;

-- SYMBOLS (agrega los que quieras)
insert into cryptolink_symbols (symbol, coingecko_id, active)
values
  ('BTC',  'bitcoin',        true),
  ('ETH',  'ethereum',       true),
  ('SOL',  'solana',         true),
  ('XRP',  'ripple',         true),
  ('ADA',  'cardano',        true),
  ('DOGE', 'dogecoin',       true),

  -- extras V5 (ejemplos)
  ('LTC',  'litecoin',       true),
  ('BNB',  'binancecoin',    true),
  ('DOT',  'polkadot',       true),
  ('LINK', 'chainlink',      true),
  ('AVAX', 'avalanche-2',    true),
  ('MATIC','matic-network',  true)
on conflict (symbol) do update
set coingecko_id = excluded.coingecko_id,
    active      = excluded.active;
