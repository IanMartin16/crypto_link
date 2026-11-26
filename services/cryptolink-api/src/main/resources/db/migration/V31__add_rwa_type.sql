ALTER TABLE cryptolink_symbols ADD COLUMN rwa_type TEXT;   -- NULL = sin clasificar (se ignora)

UPDATE cryptolink_symbols SET rwa_type='treasury'
 WHERE upper(symbol) IN ('OUSG','USTB','USTBL','MTBILL','THBILL','JTRSY','USDY','USYC','VBILL');

UPDATE cryptolink_symbols SET rwa_type='credit'
 WHERE upper(symbol) IN ('ACRED','ACRDX','JAAA','ONYC','MHYPER','NOPAL','FIUSD',
                         'BCAP','FIGR_HELOC','PC0000031','PC0000097');

UPDATE cryptolink_symbols SET rwa_type='commodity'
 WHERE upper(symbol) IN ('PAXG','XAUT','XAUM','PGOLD','KAU','KAG');

UPDATE cryptolink_symbols SET rwa_type='equity'
 WHERE upper(symbol) IN ('NVDAX','NVDAB','GOOGLB','GOOGLX','TSLAX','MSTRX','MSTRB','INTCB',
                         'CRCLB','CRCLON','CRCLX','SPCXB','SPCXX','SNDKB','STRCX','STRCON','BNCB','GGBR','SECZ');

UPDATE cryptolink_symbols SET rwa_type='etf'
 WHERE upper(symbol) IN ('SPYON','SPYX','QQQX','QQQON','IVVON','MUB','SOXLB');

UPDATE cryptolink_symbols SET rwa_type='stable'
 WHERE upper(symbol) IN ('BUIDL','BRSRV','YLDS','ALFW');

UPDATE cryptolink_symbols SET rwa_type='token'
 WHERE upper(symbol) IN ('ONDO','WLFI','CFG','HASH','CARDS','RED','ASSET','RNT','SHARE','IXS','VEREM');

CREATE INDEX IF NOT EXISTS idx_symbols_rwa_type ON cryptolink_symbols(rwa_type);