CREATE TABLE price_daily (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fiat          VARCHAR(10)  NOT NULL,
    symbol        VARCHAR(32)  NOT NULL,
    day           DATE         NOT NULL,        -- el día (UTC) que resume
    open          NUMERIC      NOT NULL,        -- primer precio del día
    high          NUMERIC      NOT NULL,        -- máximo del día
    low           NUMERIC      NOT NULL,        -- mínimo del día
    close         NUMERIC      NOT NULL,        -- último precio del día
    avg_price     NUMERIC,                      -- promedio (opcional, útil)
    volume_avg    NUMERIC,                      -- volumen promedio del día
    market_cap    NUMERIC,                      -- market cap de cierre
    points        INT          NOT NULL,        -- cuántos puntos se agregaron (transparencia)
    UNIQUE (fiat, symbol, day)                  -- un registro por símbolo por día
);

-- índice para leer la serie histórica de un símbolo (gráficas 1Y/5Y)
CREATE INDEX idx_price_daily_symbol_day ON price_daily (fiat, symbol, day);