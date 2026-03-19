from datetime import datetime, timezone
from clients.coingecko_client import fetch_trending
from adapters.coingecko_adapter import map_trending_to_basic_signals


async def get_basic_signals(window: str = "1h", assets: list[str] | None = None, limit: int = 3):
    trending = await fetch_trending()

    result = map_trending_to_basic_signals(
        trending=trending,
        window=window,
        ts=datetime.now(timezone.utc).isoformat(),
        assets_filter=assets,
        limit=limit,
    )

    return result