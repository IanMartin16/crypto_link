insert into cryptolink_symbols (symbol, coingecko_id, active)
values ('APT', 'aptos', true),
       ('ALGO', 'algorand', true),
       ('XTZ', 'tezos', true),
       ('EGLD', 'elrond-erd-2', true),
       ('FET', 'fetch-ai', true),
       ('RENDER', 'render-token', true),
       ('ETHFI', 'ether-fi', true),
       ('GRT', 'the-graph', true),
       ('JUP', 'jupiter-exchange-solana', true),
       ('PYTH', 'pyth-network', true)
on conflict (symbol) do update
set coingecko_id = excluded.coingecko_id,
    active      = excluded.active;