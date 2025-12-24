create table if not exists cryptolink_symbols (
  symbol        varchar(16) primary key,
  coingecko_id  varchar(128) not null,
  active        boolean not null default true,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

-- Seeds (puedes ampliar la lista)
insert into cryptolink_symbols (symbol, coingecko_id, active) values
  ('BTC', 'bitcoin', true),
  ('ETH', 'ethereum', true),
  ('SOL', 'solana', true),
  ('XRP', 'ripple', true),
  ('ADA', 'cardano', true),
  ('DOGE','dogecoin', true)
on conflict (symbol) do nothing;
