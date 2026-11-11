from app.services.airdrop_hub_sync import _configured_discovery_sources


def test_discovery_sources_default_to_no_key_public_providers(monkeypatch):
    monkeypatch.delenv("COINGECKO_DEMO_API_KEY", raising=False)
    monkeypatch.delenv("AIRDROP_GITHUB_RAW_SOURCE_APPROVALS", raising=False)

    sources = _configured_discovery_sources()

    assert [name for name, _url, _fetcher in sources] == [
        "dexscreener-new-pools",
        "binance-announcements",
    ]


def test_credentialed_and_approved_sources_are_opt_in(monkeypatch):
    monkeypatch.setenv("COINGECKO_DEMO_API_KEY", "configured-demo-key")
    monkeypatch.setenv("AIRDROP_GITHUB_RAW_SOURCE_APPROVALS", '[{"review_status":"approved"}]')

    sources = _configured_discovery_sources()

    assert {name for name, _url, _fetcher in sources} == {
        "dexscreener-new-pools",
        "binance-announcements",
        "coingecko-airdrop-category",
        "github-airdrop-lists",
    }
