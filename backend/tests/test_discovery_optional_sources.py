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



def test_dexscreener_query_uses_airdrop_when_variable_is_absent_or_blank(monkeypatch):
    from app.services.airdrop_hub_sync import _configured_dexscreener_search_queries

    monkeypatch.delenv("DEXSCREENER_SEARCH_QUERIES", raising=False)
    assert _configured_dexscreener_search_queries() == ["airdrop"]

    monkeypatch.setenv("DEXSCREENER_SEARCH_QUERIES", " ,   , ")
    assert _configured_dexscreener_search_queries() == ["airdrop"]


def test_dexscreener_query_keeps_configured_terms_and_deduplicates(monkeypatch):
    from app.services.airdrop_hub_sync import _configured_dexscreener_search_queries

    monkeypatch.setenv("DEXSCREENER_SEARCH_QUERIES", "airdrop, defi,airdrop")
    assert _configured_dexscreener_search_queries() == ["airdrop", "defi"]


def test_coingecko_category_normalizer_accepts_documented_category_id_field():
    from app.services.airdrop_hub_sync import _normalize_coingecko_categories
    from app.services.airdrop_discovery_filter import select_coingecko_airdrop_categories

    categories = _normalize_coingecko_categories([
        {"category_id": "airdrop", "name": "Airdrops"},
        {"id": "airdrop-hunter", "name": "Airdrop Hunter"},
        {"category_id": "", "name": "Invalid"},
        {"category_id": "missing-name"},
    ])

    assert categories == [
        {"id": "airdrop", "name": "Airdrops"},
        {"id": "airdrop-hunter", "name": "Airdrop Hunter"},
    ]
    assert select_coingecko_airdrop_categories(categories) == ["airdrop", "airdrop-hunter"]
