CREATE TABLE historical_prices (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fiat               VARCHAR(10)  NOT NULL,
    symbol             VARCHAR(32)  NOT NULL,
    price              NUMERIC      NOT NULL,
    market_cap         NUMERIC,
    volume_24h         NUMERIC,
    change_24h         NUMERIC,
    source_updated_at  TIMESTAMPTZ,              -- last_updated_at de CoinGecko (su reloj)
    captured_at        TIMESTAMPTZ  NOT NULL DEFAULT now()   -- cuándo el job guardó (tu reloj)
);

-- Índice para leer la serie temporal de un símbolo (lo que histórico y breadth necesitan)
CREATE INDEX idx_historical_prices_symbol_time
    ON historical_prices (fiat, symbol, captured_at);

-- Índice para barridos por tiempo (breadth: "todos los símbolos en un instante"
-- y la limpieza de retención por captured_at)
CREATE INDEX idx_historical_prices_time
    ON historical_prices (captured_at);
