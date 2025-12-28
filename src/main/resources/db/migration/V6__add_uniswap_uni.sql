insert into cryptolink_symbols (symbol, coingecko_id, active)
values ('UNI', 'uniswap', true)
on conflict (symbol) do update
set coingecko_id = excluded.coingecko_id,
    active      = excluded.active;
