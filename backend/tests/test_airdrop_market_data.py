import asyncio

import httpx

from app.services.airdrop_market_data import (
    build_dex_batches,
    collect_market_signals,
    parse_dex_pairs,
    parse_gecko_trending_tokens,
    refresh_campaign_market_data,
)
from app.models import AirdropCampaign


def _campaign(network="ethereum", address="0x" + "a" * 40, **overrides):
    values = {
        "id": __import__("uuid").uuid4(),
        "source_key": "market-test:" + __import__("uuid").uuid4().hex,
        "source_name": "test",
        "source_url": "https://source.example/feed",
        "name": "Test campaign",
        "description": "",
        "campaign_type": "unknown",
        "campaign_status": "ongoing",
        "token_network": network,
        "token_contract_address": address,
        "dex_pair_count": 7,
        "dex_liquidity_usd": 12345,
        "dex_volume_24h_usd": 67890,
        "gecko_trending": True,
    }
    values.update(overrides)
    return AirdropCampaign(**values)


def test_dex_batches_only_explicit_contracts_and_maximum_30_per_request():
    campaigns = [
        _campaign(address="0x" + format(index, "040x"))
        for index in range(31)
    ]
    campaigns.extend([
        _campaign(address="0x" + "a" * 40),  # duplicate
        _campaign(network="unknown-chain", address="0x" + "b" * 40),
        _campaign(address=""),
    ])

    batches = build_dex_batches(campaigns)

    assert [len(batch["addresses"]) for batch in batches] == [30, 1]
    assert all(batch["chain_id"] == "ethereum" for batch in batches)
    assert sum(len(batch["addresses"]) for batch in batches) == 31


def test_dex_parser_requires_exact_chain_and_contract_and_uses_best_pair():
    target = "0x" + "a" * 40
    payload = {"pairs": [
        {
            "chainId": "ethereum",
            "pairAddress": "pool-large",
            "baseToken": {"address": target},
            "quoteToken": {"address": "0x" + "b" * 40},
            "liquidity": {"usd": 15000},
            "volume": {"h24": 5000},
        },
        {
            "chainId": "ethereum",
            "pairAddress": "pool-busy",
            "baseToken": {"address": "0x" + "c" * 40},
            "quoteToken": {"address": target},
            "liquidity": {"usd": 3000},
            "volume": {"h24": 9000},
        },
        {
            "chainId": "base",
            "pairAddress": "wrong-chain",
            "baseToken": {"address": target},
            "liquidity": {"usd": 999999},
            "volume": {"h24": 999999},
        },
        {
            "chainId": "ethereum",
            "pairAddress": "same-ticker-not-same-address",
            "baseToken": {"address": "0x" + "d" * 40},
            "quoteToken": {"address": "0x" + "e" * 40},
            "liquidity": {"usd": 999999},
            "volume": {"h24": 999999},
        },
    ]}

    result = parse_dex_pairs(
        payload, chain_id="ethereum", requested_addresses=[target]
    )[("ethereum", target)]

    assert result["pair_count"] == 2
    assert result["pair_address"] == "pool-large"
    assert result["liquidity_usd"] == 15000
    assert result["volume_24h_usd"] == 5000


def test_dex_successful_empty_result_is_distinct_from_provider_failure():
    target = "0x" + "a" * 40
    result = parse_dex_pairs(
        {"pairs": []}, chain_id="ethereum", requested_addresses=[target]
    )[("ethereum", target)]
    assert result["pair_count"] == 0
    assert result["liquidity_usd"] == 0.0
    assert result["volume_24h_usd"] == 0.0


def test_gecko_parser_reads_global_trending_pool_token_relationships():
    payload = {
        "data": [{
            "relationships": {
                "base_token": {"data": {"id": "eth_0x" + "a" * 40}},
                "quote_token": {"data": {"id": "eth_0x" + "b" * 40}},
            }
        }],
        "included": [{"type": "token", "id": "solana_So11111111111111111111111111111111111111112"}],
    }
    result = parse_gecko_trending_tokens(payload)
    assert ("ethereum", "0x" + "a" * 40) in result
    assert ("ethereum", "0x" + "b" * 40) in result
    assert ("solana", "So11111111111111111111111111111111111111112") in result


def test_collect_market_signals_batches_dex_and_calls_gecko_once():
    campaign_a = _campaign(address="0x" + "a" * 40)
    campaign_b = _campaign(address="0x" + "b" * 40)
    requested_urls = []

    async def handler(request):
        requested_urls.append(str(request.url))
        if "tokens/v1" in request.url.path:
            return httpx.Response(200, json=[{
                "chainId": "ethereum",
                "pairAddress": "pool-a",
                "baseToken": {"address": "0x" + "a" * 40},
                "quoteToken": {"address": "0x" + "c" * 40},
                "liquidity": {"usd": 2500},
                "volume": {"h24": 3500},
            }])
        assert request.headers["Accept"] == "application/json;version=20230203"
        return httpx.Response(200, json={
            "data": [{
                "relationships": {
                    "base_token": {"data": {"id": "eth_0x" + "a" * 40}}
                }
            }]
        })

    sleeps = []
    async def fake_sleep(seconds):
        sleeps.append(seconds)

    async def run():
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            return await collect_market_signals(
                [campaign_a, campaign_b], client=client, sleep=fake_sleep
            )

    dex, trending = asyncio.run(run())
    assert len(requested_urls) == 2
    assert "/tokens/v1/ethereum/" in requested_urls[0]
    assert "0x" + "a" * 40 in requested_urls[0]
    assert "0x" + "b" * 40 in requested_urls[0]
    assert requested_urls[1].endswith("/networks/trending_pools")
    assert dex[("ethereum", "0x" + "a" * 40)]["pair_count"] == 1
    assert ("ethereum", "0x" + "a" * 40) in trending
    assert sleeps == [6.0]


class _Scalars:
    def __init__(self, rows):
        self.rows = rows

    def __iter__(self):
        return iter(self.rows)


class _FakeDb:
    def __init__(self, rows):
        self.rows = rows
        self.added = []
        self.committed = False

    async def scalars(self, statement):
        return _Scalars(self.rows)

    def add(self, row):
        self.added.append(row)

    async def commit(self):
        self.committed = True


def test_provider_failures_preserve_last_known_market_fields():
    campaign = _campaign()
    async def handler(request):
        return httpx.Response(503, json={"error": "temporarily unavailable"})

    async def fake_sleep(seconds):
        return None

    async def run():
        db = _FakeDb([campaign])
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            result = await refresh_campaign_market_data(
                db, client=client, sleep=fake_sleep
            )
        return db, result

    db, result = asyncio.run(run())
    assert result == {
        "campaigns_considered": 1,
        "dex_updated": 0,
        "gecko_updated": 0,
    }
    assert campaign.dex_pair_count == 7
    assert campaign.dex_liquidity_usd == 12345
    assert campaign.dex_volume_24h_usd == 67890
    assert campaign.gecko_trending is True
    assert db.added == []
    assert db.committed is True
