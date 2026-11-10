from pydantic import BaseModel, HttpUrl

from fastapi import APIRouter


router = APIRouter(prefix="/api", tags=["sources"])


class SourceResponse(BaseModel):
    """A curated directory entry; this endpoint does not scrape source websites."""

    id: str
    name: str
    description: str
    website_url: HttpUrl
    icon_url: HttpUrl
    category: str
    access_mode: str
    note: str


# Static navigation metadata only. The app opens the original website; no source
# content is copied into our database.
SOURCES: list[SourceResponse] = [
    SourceResponse(
        id="airdrops-io",
        name="Airdrops.io",
        description="空投活动聚合与项目指南，适合浏览近期活动和参与说明。",
        website_url="https://airdrops.io/",
        icon_url="https://www.google.com/s2/favicons?domain=airdrops.io&sz=128",
        category="空投聚合",
        access_mode="网站导航",
        note="活动详情和参与条件以网站原文及项目官方公告为准。",
    ),
    SourceResponse(
        id="defillama",
        name="DeFiLlama",
        description="DeFi 数据分析平台，提供协议数据及无代币项目发现入口。",
        website_url="https://defillama.com/airdrops",
        icon_url="https://www.google.com/s2/favicons?domain=defillama.com&sz=128",
        category="项目发现",
        access_mode="网站导航",
        note="此入口打开 DeFiLlama 的 Airdrops 页面。",
    ),
    SourceResponse(
        id="coinmarketcap-airdrops",
        name="CoinMarketCap Airdrops",
        description="加密货币行情平台提供的空投活动页面。",
        website_url="https://coinmarketcap.com/airdrop/",
        icon_url="https://www.google.com/s2/favicons?domain=coinmarketcap.com&sz=128",
        category="空投聚合",
        access_mode="网站导航",
        note="参与前请核对活动规则及官方项目链接。",
    ),
    SourceResponse(
        id="galxe",
        name="Galxe",
        description="Web3 活动与凭证任务平台，可浏览项目 Space 和活动。",
        website_url="https://app.galxe.com/",
        icon_url="https://www.google.com/s2/favicons?domain=galxe.com&sz=128",
        category="任务平台",
        access_mode="网站导航",
        note="部分活动需要登录并连接钱包；请自行核实签名请求。",
    ),
    SourceResponse(
        id="layer3",
        name="Layer3",
        description="Web3 学习、探索和任务活动平台。",
        website_url="https://layer3.xyz/",
        icon_url="https://www.google.com/s2/favicons?domain=layer3.xyz&sz=128",
        category="任务平台",
        access_mode="网站导航",
        note="任务资格、奖励和可用地区以平台显示为准。",
    ),
    SourceResponse(
        id="zealy",
        name="Zealy",
        description="社区任务与积分活动平台，可探索不同社区的任务。",
        website_url="https://zealy.io/",
        icon_url="https://www.google.com/s2/favicons?domain=zealy.io&sz=128",
        category="任务平台",
        access_mode="网站导航",
        note="不同社区有各自的规则，部分内容需要登录。",
    ),
    SourceResponse(
        id="questn",
        name="QuestN",
        description="Web3 社区任务与活动发现平台。",
        website_url="https://questn.com/",
        icon_url="https://www.google.com/s2/favicons?domain=questn.com&sz=128",
        category="任务平台",
        access_mode="网站导航",
        note="活动页面及奖励状态以平台和项目方公告为准。",
    ),
]


@router.get("/sources", response_model=list[SourceResponse])
async def list_sources() -> list[SourceResponse]:
    """Return the app's curated website directory."""
    return SOURCES
