from fastapi import APIRouter, Query
from services.basic_signals_service import get_basic_signals
from models.basic_signals import SocialLinkBasicSignalsResponse

router = APIRouter()


@router.get("/v1/basic-signals", response_model=SocialLinkBasicSignalsResponse)
async def basic_signals(
    window: str = Query("1h"),
    assets: str | None = Query(None),
    limit: int = Query(3, ge=1, le=10),
):
    asset_list = [x.strip().upper() for x in assets.split(",")] if assets else None
    return await get_basic_signals(window=window, assets=asset_list, limit=limit)