CREATE TABLE IF NOT EXISTS price_history (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fiat         VARCHAR(10)  NOT NULL,
    symbol       VARCHAR(32)  NOT NULL,
    price        NUMERIC      NOT NULL,
    change_24h   NUMERIC,
    market_cap   NUMERIC,
    captured_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- índice para leer "la serie de un símbolo en el tiempo" (lo que usan los derivados)
CREATE INDEX idx_price_history_symbol_time
    ON price_history (fiat, symbol, captured_at);