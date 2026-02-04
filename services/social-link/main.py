from fastapi import FastAPI, Query
from datetime import datetime, timezone

app = FastAPI(title="social-link", version="0.1.0")

@app.get("/health")
def health():
    return {"ok": True}

@app.get("/internal/v1/trends")
def trends(symbols: str = Query(default="BTC,ETH")):
    syms = [s.strip().upper() for s in symbols.split(",") if s.strip()]
    now = datetime.now(timezone.utc).isoformat()
    data = [{"symbol": s, "trend": "up", "score": 0.72, "reason": "mvp: placeholder"} for s in syms]
    return {"ts": now, "data": data}
