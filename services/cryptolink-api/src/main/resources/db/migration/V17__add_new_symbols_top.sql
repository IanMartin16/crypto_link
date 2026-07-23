insert into cryptolink_symbols (symbol, coingecko_id, active)
values ('FIGR_HELOC', 'figure-heloc', true),
       ('RAIN', 'rain', true),
       ('WBT', 'whitebit', true),
       ('AWETH', 'aave-v3-weth', true),
       ('JST', 'just', true),
       ('USDY', 'ondo-us-dollar-yield', true),
       ('LAB', 'lab', true),
       ('HTX', 'htx-dao', true),
       ('USDF', 'falcon-finance', true),
       ('BFUSD', 'bfusd', true),
       ('EUTBL', 'eutbl', true),
       ('BDX', 'beldex', true),
       ('BCAP', 'blockchain-capital', true),
       ('USDGO', 'usdgo', true),
       ('NEXO', 'nexo', true),
       ('ENA', 'ethena', true),
       ('ADI', 'adi-token', true),
       ('GT', 'gatechain-token', true),
       ('BUIDL', 'blackrock-usd-institutional-digital-liquidity-fund', true),
       ('NIGHT', 'midnight-3', true)
on conflict (symbol) do update
set coingecko_id = excluded.coingecko_id,
    active      = excluded.active;