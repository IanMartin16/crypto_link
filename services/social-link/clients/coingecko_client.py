import httpx

COINGECKO_TRENDING_URL = "https://api.coingecko.com/api/v3/search/trending"


async def fetch_trending():
    async with httpx.AsyncClient(timeout=12.0) as client:
        res = await client.get(
            COINGECKO_TRENDING_URL,
            headers={"accept": "application/json"},
        )
        res.raise_for_status()
        return res.json()