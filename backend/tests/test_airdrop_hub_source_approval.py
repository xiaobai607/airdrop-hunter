from app.services import airdrop_hub_sync


def test_unapproved_legacy_directory_remains_observation_only(monkeypatch):
    monkeypatch.delenv("AIRDROP_HUB_SOURCE_APPROVED", raising=False)
    monkeypatch.setattr(airdrop_hub_sync, "TRUSTED_AIRDROP_PROVIDER_LABELS", {})
    assert airdrop_hub_sync._source_publication_state("web3-discover") == (
        "TRUST_LEVEL_0",
        "OBSERVATION",
    )


def test_binance_is_not_promoted_to_trusted_catalog_provider():
    assert airdrop_hub_sync._source_publication_state("binance-announcements") == (
        "TRUST_LEVEL_0",
        "OBSERVATION",
    )


def test_dexscreener_keeps_successful_queries_when_one_term_fails(monkeypatch):
    import asyncio

    import httpx

    monkeypatch.setenv("DEXSCREENER_SEARCH_QUERIES", "BROKEN,ETH")

    class PartialClient:
        async def get(self, url, params):
            if params["q"] == "BROKEN":
                raise httpx.ConnectError("temporary failure")
            request = httpx.Request("GET", url, params=params)
            return httpx.Response(
                200,
                json={
                    "pairs": [{
                        "baseToken": {"name": "Example", "symbol": "EX", "address": "0xabc"},
                        "chainId": "ethereum",
                        "url": "https://dexscreener.com/ethereum/0xabc",
                        "pairCreatedAt": 1790000000000,
                        "liquidity": {"usd": 1000},
                        "volume": {"h24": 500},
                        "txns": {"h24": {"buys": 2, "sells": 1}},
                    }]
                },
                request=request,
            )

    rows = asyncio.run(airdrop_hub_sync._fetch_dexscreener_observations(PartialClient()))

    assert len(rows) == 1
    assert rows[0]["source_url"].endswith("?q=ETH")
    assert rows[0]["_filter_metadata"]["query"] == "ETH"


def test_dexscreener_fails_only_when_no_search_term_produces_rows(monkeypatch):
    import asyncio

    import httpx
    import pytest

    monkeypatch.setenv("DEXSCREENER_SEARCH_QUERIES", "BROKEN")

    class FailedClient:
        async def get(self, url, params):
            raise httpx.ConnectError("temporary failure")

    with pytest.raises(RuntimeError, match="所有搜索词均失败"):
        asyncio.run(airdrop_hub_sync._fetch_dexscreener_observations(FailedClient()))
