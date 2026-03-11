insert into cryptolink_symbols (symbol, coingecko_id, active)
values ('HYPE', 'hyperliquid', true),
       ('PYUSD', 'paypalusd', true),
       ('SHIB', 'shiba-inu', true),
       ('POL', 'polygon-ecosystem-token', true),
       ('TON', 'toncoin', true)
on conflict (symbol) do update
set coingecko_id = excluded.coingecko_id,
    active      = excluded.active;