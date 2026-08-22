insert into cryptolink_symbols (symbol, coingecko_id, active)
values ('USYC', 'hashnote-usyc', true),
       ('USD0', 'usual-usd', true),
       ('A7A5', 'a7a5', true),
       ('TUSD', 'true-usd', true),
       ('HASH', 'hash-2', true),
       ('USX', 'usx', true),
       ('PUMP', 'pump-fun', true),
       ('JAAA', 'janus-henderson-anemoy-aaa-clo-fund', true),
       ('GHO', 'gho', true),
       ('VVV', 'venice-token', true),
       ('YLDS', 'ylds', true),
       ('FIL', 'filecoin', true),
       ('EURSAFO', 'spiko-amundi-overnight-swap-fund-eur', true),
       ('币安人生', 'bianrensheng', true),
       ('XDC', 'xdce-crowd-sale', true),
       ('PENGU', 'pudgy-penguins', true),
       ('TRUMP', 'official-trump', true),
       ('ZAMA', 'zama', true),
       ('USTB', 'superstate-short-duration-us-government-securities-fund-ustb', true),
       ('JTRSY', 'janus-henderson-anemoy-treasury-fund', true)
on conflict (symbol) do update
set coingecko_id = excluded.coingecko_id,
    active      = excluded.active;