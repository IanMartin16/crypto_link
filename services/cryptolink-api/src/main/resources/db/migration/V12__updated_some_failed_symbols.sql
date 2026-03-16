insert into cryptolink_symbols (symbol, coingecko_id, active)
values ('OKB', 'okb', true),
       ('PYUSD', 'paypal-usd', true),
       ('PI', 'pi-network', true),
       ('LEO', 'leo-token', true),
       ('XMR', 'monero', true),
       ('USDE', 'ethena-usde', true),
       ('CC', 'canton-network', true),
       ('WLFI', 'world-liberty-financial', true),
       ('HBAR', 'hedera-hashgraph', true),
       ('MNT', 'mantle', true),
       ('PAXG', 'pax-gold', true),
       ('TON', 'the-open-network', true)
on conflict (symbol) do update
set coingecko_id = excluded.coingecko_id,
    active      = excluded.active;