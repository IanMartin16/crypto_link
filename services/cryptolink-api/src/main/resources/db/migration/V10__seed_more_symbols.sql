insert into cryptolink_symbols (symbol, coingecko_id, active)
values ('USDT', 'tether', true),
       ('USDC', 'usd-coin', true),
       ('DAI', 'dai', true),
       ('BCH', 'bitcoin-cash', true),
       ('XLM', 'stellar', true),
       ('SHIBA', 'shiba-inu', true),
       ('TRX', 'tron', true),
       ('ATOM', 'cosmos', true),
       ('ARB', 'arbitrum', true),
       ('VET', 'vechain', true),
       ('FTM', 'fantom', true),
       ('OP', 'optimism', true),
       ('NEAR', 'near', true),
       ('SUI', 'sui', true)
on conflict (symbol) do update
set coingecko_id = excluded.coingecko_id,
    active      = excluded.active;