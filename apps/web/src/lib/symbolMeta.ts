export const SYMBOL_META: Record<string, { name: string }> = {
 BTC: { name: "Bitcoin" },
  ETH: { name: "Ethereum" },
  SOL: { name: "Solana" },
  XRP: { name: "XRP" },
  ADA: { name: "Cardano" },
  BNB: { name: "BNB" },
  DOGE: { name: "Dogecoin" },
  MATIC: { name: "Polygon" },
  AVAX: { name: "Avalanche" },
  DOT: { name: "Polkadot" },
  LINK: { name: "Chainlink" },
  UNI: { name: "Uniswap" },
  LTC: { name: "Litecoin" },
  USDT:{ name:"Tether" },
  USDC:{ name: "USDC" },
  SHIB:{ name: "SHIBA" },
  DAI:{ name: "DAI" },
  BCH:{ name: "Bitcoin Cash" },
  XLM:{ name: "Stellar" },
  NEAR:{ name: "NEAR" },
  VET:{ name: "VeChain" },
  TRX:{ name: "TRON" },
  ATOM:{ name: "Cosmos" },
  SUI: { name: "Sui" },
  ARB: { name: "Arbitrum" },
  FTM: { name: "Fantom" },
  OP: { name: "Optimism"},
};
export function getSymbolName(symbol: string) {
  const key = symbol.toUpperCase();
  return SYMBOL_META[key]?.name;
}