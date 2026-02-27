const OVERRIDES: Record<string, string> = {
  BTC: "/icons/crypto/btc.png",
  ETH: "/icons/crypto/eth.png",
  SOL: "/icons/crypto/sol.png",
  XRP: "/icons/crypto/xrp.png",
  ADA: "/icons/crypto/ada.png",
  DOGE: "/icons/crypto/doge.png",
  BNB: "/icons/crypto/bnb.png",
  UNI: "/icons/crypto/uni.png",
  LTC: "/icons/crypto/ltc.png",
  DOT: "/icons/crypto/dot.png",
  AVAX: "/icons/crypto/avax.png",
  MATIC: "/icons/crypto/matic.png",
  LINK: "/icons/crypto/link.png",
  USDT: "/icons/crypto/usdt.png",
  USDC: "/icons/crypto/usdc.png",
  SHIB: "/icons/crypto/shib.png",
  DAI: "/icons/crypto/dai.png",
  BCH: "/icons/crypto/bch.png",
  XLM: "/icons/crypto/xlm.png",
  NEAR: "/icons/crypto/near.png",
  VET: "/icons/crypto/vet.png",
  TRX: "/icons/crypto/trx.png",
  ATOM: "/icons/crypto/atom.png",
  SUI: "/icons/crypto/sui.png",
  ARB: "/icons/crypto/arb.png",
  FTM: "/icons/crypto/ftm.png",
  OP: "/icons/crypto/op.png",
  
};

export function getCryptoIconUrl(symbol: string): string | undefined {
  return OVERRIDES[symbol.toUpperCase()];
}