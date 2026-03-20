from fastapi import FastAPI, Query
from datetime import datetime, timezone
from fastapi.middleware.cors import CORSMiddleware
from services.basic_signals_service import get_basic_signals


app = FastAPI(title="social-link", version="0.1.0")


def normalize_symbol(symbol: str | None) -> str:
    return (symbol or "").strip().upper()


def infer_tags(symbol: str, name: str | None = None) -> list[str]:
    s = symbol.upper()
    n = (name or "").lower()
    tags: list[str] = []

    if s in {"BTC", "ETH"}:
        tags.append("majors-led")
    if s in {"SOL", "ADA", "AVAX", "ATOM", "NEAR"}:
        tags.append("layer1")
    if s in {"DOGE", "SHIB", "PEPE", "FLOKI"}:
        tags.append("meme")
    if s in {"LINK", "UNI", "AAVE", "MKR"}:
        tags.append("defi")
    if "bitcoin" in n:
        tags.append("store-of-value")
    if "ethereum" in n:
        tags.append("smart-contracts")

    return list(dict.fromkeys(tags))


ALLOWED_SOCIAL_ASSETS = {
    "BTC", "ETH", "SOL", "DOGE", "SHIB", "ADA", "AVAX",
    "ATOM", "LINK", "UNI", "AAVE", "NEAR", "MKR",
    "PEPE", "XRP", "HYPE",
}

FALLBACK_ASSETS = ["BTC", "ETH", "SOL"]

NARRATIVE_TAGS = {
    "majors-led",
    "layer1",
    "meme",
    "defi",
    "selective breadth",
    "mixed participation",
    "trend expansion",
    "risk-off",
}
app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:3000",
        "https://cryptolink.mx",
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

def attention_score_from_rank(rank: int | None, index: int = 0) -> float:
    safe_rank = rank if isinstance(rank, int) and rank > 0 else 9999

    if safe_rank <= 2:
        rank_component = 92
    elif safe_rank <= 5:
        rank_component = 84
    elif safe_rank <= 10:
        rank_component = 74
    elif safe_rank <= 25:
        rank_component = 60
    elif safe_rank <= 50:
        rank_component = 48
    else:
        rank_component = 34

    position_penalty = index * 10
    return max(18, min(100, rank_component - position_penalty))


def attention_delta_from_price_change(change, index: int = 0) -> float:
    if isinstance(change, (int, float)):
        return round(float(change), 2)

    fallback = 8 - index * 1.5
    return round(max(1, fallback), 2)


def direction_from_delta(delta: float) -> str:
    if delta > 0.1:
        return "up"
    if delta < -0.1:
        return "down"
    return "flat"


def ensure_minimum_leaders(leaders: list[dict]) -> list[dict]:
    existing = {x["asset"] for x in leaders}
    fillers: list[dict] = []

    for index, asset in enumerate(FALLBACK_ASSETS):
        if asset in existing:
            continue
        fillers.append({
            "asset": asset,
            "attentionScore": 45 - index * 5,
            "attentionDeltaPct": 0,
            "direction": "flat",
            "tags": infer_tags(asset, asset),
        })

    return (leaders + fillers)[:3]


def derive_coverage(top_assets: list[str], tags: list[str]) -> str:
    if len(top_assets) >= 4 and len(tags) >= 4:
        return "broad"
    if len(top_assets) >= 2 and len(tags) >= 2:
        return "moderate"
    return "low"


def build_basic_signals(window: str = "1h", assets: list[str] | None = None, limit: int = 3):
    # MVP temporal con mock interno; luego aquí entra CoinGecko real
    mock_trending = [
        {
            "item": {
                "id": "bitcoin",
                "coin_id": 1,
                "name": "Bitcoin",
                "symbol": "BTC",
                "market_cap_rank": 1,
                "data": {"price_change_percentage_24h": {"usd": 2.4}},
            }
        },
        {
            "item": {
                "id": "solana",
                "coin_id": 4128,
                "name": "Solana",
                "symbol": "SOL",
                "market_cap_rank": 5,
                "data": {"price_change_percentage_24h": {"usd": 5.1}},
            }
        },
        {
            "item": {
                "id": "ethereum",
                "coin_id": 1027,
                "name": "Ethereum",
                "symbol": "ETH",
                "market_cap_rank": 2,
                "data": {"price_change_percentage_24h": {"usd": 1.6}},
            }
        },
    ]

    allowed = set(assets) if assets else ALLOWED_SOCIAL_ASSETS

    leaders: list[dict] = []
    for index, entry in enumerate(mock_trending):
        item = entry.get("item", {})
        asset = normalize_symbol(item.get("symbol"))
        if not asset or asset not in allowed:
            continue

        change = (((item.get("data") or {}).get("price_change_percentage_24h") or {}).get("usd"))
        delta = attention_delta_from_price_change(change, index)

        leaders.append({
            "asset": asset,
            "attentionScore": attention_score_from_rank(item.get("market_cap_rank"), index),
            "attentionDeltaPct": delta,
            "direction": direction_from_delta(delta),
            "tags": infer_tags(asset, item.get("name")),
        })

    leaders = ensure_minimum_leaders(leaders)[: max(limit, 3)]
    top_assets = [x["asset"] for x in leaders[:3]]

    raw_tags: list[str] = []
    for leader in leaders:
        raw_tags.extend(leader.get("tags", []))

    raw_tags = list(dict.fromkeys(raw_tags))
    narrative_tags = [tag for tag in raw_tags if tag in NARRATIVE_TAGS]
    tags = narrative_tags[:4] if narrative_tags else raw_tags[:4]

    coverage = derive_coverage(top_assets, tags)

    now = datetime.now(timezone.utc).isoformat()

    return {
        "ok": True,
        "source": "social-link-mvp",
        "ts": now,
        "window": window,
        "market": {
            "topAssets": top_assets,
            "attentionLeaders": leaders,
            "attentionLosers": [],
            "tags": tags,
            "coverage": coverage,
        },
    }


@app.get("/health")
def health():
    return {"ok": True}


@app.get("/internal/v1/trends")
def trends(symbols: str = Query(default="BTC,ETH")):
    syms = [s.strip().upper() for s in symbols.split(",") if s.strip()]
    now = datetime.now(timezone.utc).isoformat()
    data = [{"symbol": s, "trend": "up", "score": 0.72, "reason": "mvp: placeholder"} for s in syms]
    return {"ts": now, "data": data}

    
@app.get("/internal/v1/basic-signals")
async def basic_signals(
    window: str = Query(default="1h"),
    assets: str | None = Query(default=None),
    limit: int = Query(default=3),
):
    asset_list = [s.strip().upper() for s in assets.split(",")] if assets else None
    result = await get_basic_signals(window=window, assets=asset_list, limit=limit)
    return result