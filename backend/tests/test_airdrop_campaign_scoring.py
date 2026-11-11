from datetime import datetime, timezone
from uuid import uuid4

from app.models import AirdropCampaign, AirdropCampaignScoreHistory
from app.services.airdrop_campaign_scoring import (
    _has_verified_official_url,
    build_campaign_scoring_input,
    recalculate_campaign_scores,
)


def _campaign(**overrides):
    values = {
        "id": uuid4(),
        "source_key": f"test:{uuid4()}",
        "source_name": "test",
        "source_url": "https://source.example/feed",
        "name": "Test campaign",
        "description": "",
        "official_url": "https://project.example/airdrop",
        "campaign_type": "unknown",
        "campaign_status": "ongoing",
        "trust_status": "verified",
        "temperature": 82,
        "is_kyc_free": True,
        "tier": "observation",
        "is_published": False,
        "score_breakdown": {},
        "scoring_version": "v1",
        "listing_reason": "",
        "review_reason": "",
        "risk_flags": [],
        "source_conflict": False,
        "corroborated_risk": False,
        "low_heat_success_count": 0,
        "created_at": datetime.now(timezone.utc),
    }
    values.update(overrides)
    return AirdropCampaign(**values)


def test_verified_official_url_requires_verified_trust_and_https():
    assert _has_verified_official_url(_campaign()) is True
    assert _has_verified_official_url(_campaign(trust_status="unverified")) is False
    assert _has_verified_official_url(_campaign(official_url="http://project.example")) is False
    assert _has_verified_official_url(_campaign(official_url=None)) is False
    assert _has_verified_official_url(
        _campaign(official_url="https://user:pass@project.example/airdrop")
    ) is False


def test_scoring_input_does_not_trust_rss_article_link():
    campaign = _campaign(
        trust_status="unverified",
        official_url=None,
        source_url="https://feed.example/rss.xml",
    )
    payload = build_campaign_scoring_input(campaign)
    assert payload["official_url_verified"] is False
    assert payload["trust_status"] == "unverified"


class _Scalars:
    def __init__(self, values):
        self.values = values

    def __iter__(self):
        return iter(self.values)


class _FakeDb:
    def __init__(self, campaigns):
        self.campaigns = campaigns
        self.added = []
        self.committed = False

    async def scalars(self, statement):
        return _Scalars(self.campaigns)

    def add(self, value):
        self.added.append(value)

    async def commit(self):
        self.committed = True


async def test_recalculate_campaign_scores_only_publishes_verified_campaigns():
    verified = _campaign()
    unverified = _campaign(
        trust_status="unverified",
        official_url=None,
        source_key=f"test:{uuid4()}",
    )
    db = _FakeDb([verified, unverified])

    result = await recalculate_campaign_scores(db)

    assert result == {"evaluated": 2, "updated": 2, "publishable": 1, "held": 1}
    assert verified.tier == "featured"
    assert verified.is_published is True
    assert unverified.tier == "observation"
    assert unverified.is_published is False
    assert len(db.added) == 2
    assert all(isinstance(row, AirdropCampaignScoreHistory) for row in db.added)
    assert db.committed is True


async def test_recalculation_is_idempotent_when_decision_has_not_changed():
    campaign = _campaign(
        tier="featured",
        is_published=True,
        score=76,
        score_breakdown={
            "scoring_version": "v1",
            "source_heat": 82.0,
            "temperature": 82.0,
            "opportunity_score": None,
            "source_heat_rule": "max(temperature, opportunity_score); overlapping source metrics are not added",
            "market_signal": "not_applicable",
            "trust_status": "verified",
            "gecko_trending": None,
            "bonuses": {"official_url_verified": 10},
        },
        listing_reason="热度达到70，官方链接已核验，KYC条件明确且市场门槛满足或不适用",
    )
    db = _FakeDb([campaign])

    result = await recalculate_campaign_scores(db)

    assert result == {"evaluated": 1, "updated": 0, "publishable": 0, "held": 0}
    assert db.added == []
    assert db.committed is True
