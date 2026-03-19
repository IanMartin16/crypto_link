from typing import List, Literal, Optional
from pydantic import BaseModel

Direction = Literal["up", "down", "flat"]
Coverage = Literal["low", "moderate", "broad"]
Window = Literal["15m", "30m", "1h", "4h"]


class SocialAttentionItem(BaseModel):
    asset: str
    attentionScore: float
    attentionDeltaPct: float
    direction: Direction
    tags: List[str] = []


class BasicSignalsMarket(BaseModel):
    topAssets: List[str]
    attentionLeaders: List[SocialAttentionItem]
    attentionLosers: List[SocialAttentionItem]
    tags: List[str]
    coverage: Coverage


class SocialLinkBasicSignalsResponse(BaseModel):
    ok: bool
    source: str
    ts: str
    window: Window
    market: BasicSignalsMarket