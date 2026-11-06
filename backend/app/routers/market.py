import asyncio
import re
import time
from typing import Annotated
from uuid import UUID
import httpx
from fastapi import APIRouter, Depends, HTTPException, Response
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession
from app.routers.auth import get_current_user
from app.database import get_db
from app.models import Coin, User

router = APIRouter(prefix="/api", tags=["market"])
DEFAULT_COINS = [("btc-bitcoin","BTC","Bitcoin"),("eth-ethereum","ETH","Ethereum"),("sol-solana","SOL","Solana"),("bnb-binance-coin","BNB","BNB"),("xrp-xrp","XRP","XRP")]
_cache: dict[str, tuple[float, dict]] = {}

class CoinCreate(BaseModel):
    coinpaprika_id: str = Field(min_length=3, max_length=100)
    symbol: str = Field(min_length=1, max_length=20)
    name: str = Field(min_length=1, max_length=100)
class CoinResponse(BaseModel):
    id: str
    coinpaprika_id: str
    symbol: str
    name: str

async def ticker(coin_id: str):
    if not re.fullmatch(r"[a-z0-9-]{3,100}", coin_id):
        raise ValueError("Invalid CoinPaprika identifier")
    now=time.monotonic()
    hit=_cache.get(coin_id)
    if hit and now-hit[0]<60: return hit[1]
    async with httpx.AsyncClient(timeout=8,headers={"User-Agent":"AirdropHunter/1.0"}) as client:
        response=await client.get(f"https://api.coinpaprika.com/v1/tickers/{coin_id}",params={"quotes":"USD"})
        response.raise_for_status()
        data=response.json()
    usd=(data.get("quotes") or {}).get("USD") or {}
    value={"coinpaprika_id":coin_id,"symbol":data.get("symbol",""),"name":data.get("name",""),"price_usd":usd.get("price"),"change_24h":usd.get("percent_change_24h"),"market_cap_usd":usd.get("market_cap"),"rank":data.get("rank")}
    _cache[coin_id]=(now,value)
    return value

async def owned_coins(user: User,db: AsyncSession):
    rows=await db.scalars(select(Coin).where(Coin.user_id==user.id).order_by(Coin.created_at))
    return list(rows)

@router.get("/market/prices")
async def market_prices(db: Annotated[AsyncSession,Depends(get_db)],user: Annotated[User,Depends(get_current_user)]):
    custom=await owned_coins(user,db)
    coins=list(DEFAULT_COINS); seen={x[0] for x in coins}
    for c in custom:
        if c.coinpaprika_id not in seen: coins.append((c.coinpaprika_id,c.symbol,c.name)); seen.add(c.coinpaprika_id)
    results=await asyncio.gather(*(ticker(c[0]) for c in coins),return_exceptions=True)
    prices=[]
    for fallback,result in zip(coins,results):
        if isinstance(result,Exception):
            prices.append({"coinpaprika_id":fallback[0],"symbol":fallback[1],"name":fallback[2],"price_usd":None,"change_24h":None,"market_cap_usd":None,"rank":None,"unavailable":True})
        else: prices.append({**result,"unavailable":False})
    if all(p["unavailable"] for p in prices): raise HTTPException(503,"CoinPaprika 行情暂时不可用")
    return {"provider":"CoinPaprika","currency":"USD","prices":prices}

@router.get("/coins",response_model=list[CoinResponse])
async def list_coins(db: Annotated[AsyncSession,Depends(get_db)],user: Annotated[User,Depends(get_current_user)]):
    return [CoinResponse(id=str(c.id),coinpaprika_id=c.coinpaprika_id,symbol=c.symbol,name=c.name) for c in await owned_coins(user,db)]

@router.post("/coins",response_model=CoinResponse,status_code=201)
async def add_coin(payload: CoinCreate,db: Annotated[AsyncSession,Depends(get_db)],user: Annotated[User,Depends(get_current_user)]):
    coin_id=payload.coinpaprika_id.strip().lower()
    try: data=await ticker(coin_id)
    except (httpx.HTTPError,ValueError): raise HTTPException(422,"无法验证 CoinPaprika 币种 ID")
    symbol=payload.symbol.strip().upper()
    if data["symbol"].upper()!=symbol: raise HTTPException(422,"币种代码与 CoinPaprika 数据不匹配")
    c=Coin(user_id=user.id,coinpaprika_id=coin_id,symbol=symbol,name=data["name"] or payload.name.strip())
    db.add(c)
    try: await db.commit()
    except IntegrityError:
        await db.rollback()
        raise HTTPException(409,"该币种已在关注列表中")
    await db.refresh(c)
    return CoinResponse(id=str(c.id),coinpaprika_id=c.coinpaprika_id,symbol=c.symbol,name=c.name)

@router.delete("/coins/{coin_id}",status_code=204)
async def remove_coin(coin_id: UUID,db: Annotated[AsyncSession,Depends(get_db)],user: Annotated[User,Depends(get_current_user)]):
    c=await db.scalar(select(Coin).where(Coin.id==coin_id,Coin.user_id==user.id))
    if c is None: raise HTTPException(404,"关注币种不存在")
    await db.delete(c); await db.commit()
    return Response(status_code=204)
