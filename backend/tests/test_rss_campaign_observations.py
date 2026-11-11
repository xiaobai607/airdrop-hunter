from datetime import datetime, timezone
from types import SimpleNamespace

from app.services.rss_campaign_observations import (
    _snapshot_hash,
    candidate_observation_payload,
)


def _candidate(**overrides):
    values = {
        "source_key": "a" * 64,
        "source_name": "approved-feed",
        "source_url": "https://feeds.example/rss.xml",
        "title": "Potential campaign announcement",
        "summary": "An article that mentions a possible campaign.",
        "item_url": "https://publisher.example/article",
        "published_at": datetime(2026, 11, 11, tzinfo=timezone.utc),
    }
    values.update(overrides)
    return SimpleNamespace(**values)


def test_rss_candidate_becomes_unknown_unpublished_observation_payload():
    payload = candidate_observation_payload(_candidate())

    assert payload["source_key"] == "rss-candidate:" + "a" * 64
    assert payload["source_url"] == "https://feeds.example/rss.xml"
    assert payload["item_url"] == "https://publisher.example/article"
    assert payload["official_url"] is None
    assert payload["chain"] is None
    assert payload["campaign_type"] == "unknown"
    assert payload["campaign_status"] == "unknown"
    assert payload["requires_kyc"] is None
    assert payload["is_kyc_free"] is None
    assert payload["token_contract_address"] is None
    assert payload["trust_status"] == "unverified"
    assert payload["tier"] == "observation"
    assert payload["is_published"] is False


def test_candidate_snapshot_hash_is_stable_and_changes_with_source_content():
    first = candidate_observation_payload(_candidate())
    same = candidate_observation_payload(_candidate())
    changed = candidate_observation_payload(_candidate(summary="Updated article content"))

    assert _snapshot_hash(first) == _snapshot_hash(same)
    assert _snapshot_hash(first) != _snapshot_hash(changed)


def test_missing_publication_time_remains_unknown():
    payload = candidate_observation_payload(_candidate(published_at=None))
    assert payload["published_at"] is None
