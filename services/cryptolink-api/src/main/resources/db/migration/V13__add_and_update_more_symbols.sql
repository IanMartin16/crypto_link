insert into cryptolink_symbols (symbol, coingecko_id, active)
values ('PEPE', 'pepe', true),
       ('FLOKI', 'floki', true),
       ('AAVE', 'aave', true),
       ('MKR', 'maker', true),
       ('CRO', 'crypto-com-chain', true),
       ('TAO', 'bittensor', true),
       ('USDG', 'global-dollar', true),
       ('INJ', 'injective-protocol', true),
       ('SKY', 'sky', true),
       ('ICP', 'internet-computer', true)
on conflict (symbol) do update
set coingecko_id = excluded.coingecko_id,
    active      = excluded.active;