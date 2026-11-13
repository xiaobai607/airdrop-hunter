from app.services.airdrop_scoring import evaluate_campaign


def test_temperature_and_opportunity_score_are_not_double_counted():
    result = evaluate_campaign({
        "temperature": 80, "opportunity_score": 75,
        "trust_status": "verified", "official_url_verified": True,
        "is_kyc_free": True, "campaign_status": "ongoing",
    })
    assert result["score"] == 74
    assert result["tier"] == "featured"
    assert result["is_published"] is True
    assert result["score_breakdown"]["source_heat"] == 80


def test_unknown_heat_is_not_zero_and_not_published():
    result = evaluate_campaign({"temperature": None, "opportunity_score": None})
    assert result["score"] is None
    assert result["tier"] == "observation"
    assert result["is_published"] is False


def test_tokenless_project_is_not_penalized_for_missing_dex_data():
    result = evaluate_campaign({
        "temperature": 82, "trust_status": "verified",
        "official_url_verified": True, "requires_kyc": False,
        "token_contract_address": None,
    })
    assert result["tier"] == "featured"
    assert result["score_breakdown"]["market_signal"] == "not_applicable"


def test_known_contract_requires_pair_liquidity_and_volume_for_featured():
    result = evaluate_campaign({
        "temperature": 90, "trust_status": "verified",
        "official_url_verified": True, "is_kyc_free": True,
        "token_contract_address": "0xabc", "dex_pair_count": 1,
        "dex_liquidity_usd": 2000, "dex_volume_24h_usd": 500,
    }, min_liquidity_usd=1000, min_volume_24h_usd=1000)
    assert result["tier"] == "observation"
    assert result["is_published"] is False


def test_ended_campaign_with_retroactive_possibility_is_not_archived():
    result = evaluate_campaign({
        "temperature": 55, "campaign_status": "ended",
        "retroactive_possible": True, "trust_status": "verified",
        "official_url_verified": True,
    })
    assert result["tier"] == "ordinary"


def test_ended_campaign_without_retroactive_path_is_archived():
    result = evaluate_campaign({
        "temperature": 80, "campaign_status": "ended",
        "retroactive_possible": False,
    })
    assert result["tier"] == "archived"
    assert result["is_published"] is False


def test_failed_runs_do_not_archive_by_themselves():
    result = evaluate_campaign({
        "temperature": 20, "campaign_status": "ongoing",
        "low_heat_success_count": 2,
    })
    assert result["tier"] == "observation"


def test_three_successful_low_heat_runs_archive():
    result = evaluate_campaign({
        "temperature": 20, "campaign_status": "ongoing",
        "low_heat_success_count": 3,
    })
    assert result["tier"] == "archived"


def test_corrobated_risk_is_held_out_of_publication():
    result = evaluate_campaign({
        "temperature": 95, "trust_status": "verified",
        "official_url_verified": True, "is_kyc_free": True,
        "corroborated_risk": True,
    })
    assert result["tier"] == "observation"
    assert result["is_published"] is False


def test_ordinary_campaign_without_verified_official_url_stays_observation():
    result = evaluate_campaign({
        "temperature": 55,
        "campaign_status": "ongoing",
        "trust_status": "verified",
        "official_url_verified": False,
    })
    assert result["tier"] == "observation"
    assert result["is_published"] is False
    assert "官方链接尚未核验" in result["listing_reason"]


def test_ordinary_campaign_with_verified_official_url_can_publish():
    result = evaluate_campaign({
        "temperature": 55,
        "campaign_status": "ongoing",
        "trust_status": "verified",
        "official_url_verified": True,
    })
    assert result["tier"] == "ordinary"
    assert result["is_published"] is True


def test_low_heat_archival_uses_repeated_temperature_not_opportunity_score():
    # A stale/opportunity score must not mask three fresh low-temperature runs.
    result = evaluate_campaign({
        "temperature": 20,
        "opportunity_score": 85,
        "campaign_status": "ongoing",
        "low_heat_success_count": 3,
    })
    assert result["tier"] == "archived"
    assert result["is_published"] is False


def test_two_low_temperature_runs_do_not_archive_even_if_opportunity_score_is_low():
    result = evaluate_campaign({
        "temperature": 20,
        "opportunity_score": 15,
        "campaign_status": "ongoing",
        "low_heat_success_count": 2,
    })
    assert result["tier"] == "observation"
    assert result["is_published"] is False


def test_verified_binance_crypto_airdrop_can_publish_without_invented_heat():
    result = evaluate_campaign({
        "source_name": "binance-crypto-airdrop",
        "campaign_type": "airdrop",
        "temperature": None,
        "opportunity_score": None,
        "campaign_status": "unknown",
        "trust_status": "verified",
        "official_url_verified": True,
        "requires_kyc": None,
        "is_kyc_free": None,
    })
    assert result["score"] is None
    assert result["tier"] == "ordinary"
    assert result["is_published"] is True
    assert result["score_breakdown"]["official_binance_airdrop_evidence"] is True
    assert "保持未知" in result["listing_reason"]


def test_binance_campaign_without_detail_verified_official_url_stays_unpublished():
    result = evaluate_campaign({
        "source_name": "binance-crypto-airdrop",
        "campaign_type": "airdrop",
        "trust_status": "verified",
        "official_url_verified": False,
    })
    assert result["tier"] == "observation"
    assert result["is_published"] is False


def test_latest_activities_or_other_source_cannot_use_binance_airdrop_gate():
    for payload in (
        {"source_name": "binance-announcements", "campaign_type": "airdrop"},
        {"source_name": "binance-crypto-airdrop", "campaign_type": "promotion"},
    ):
        result = evaluate_campaign({
            **payload,
            "trust_status": "verified",
            "official_url_verified": True,
            "temperature": None,
            "opportunity_score": None,
        })
        assert result["tier"] == "observation"
        assert result["is_published"] is False


def test_binance_airdrop_publication_still_honors_risk_and_source_conflict():
    for payload in ({"corroborated_risk": True}, {"source_conflict": True}):
        result = evaluate_campaign({
            "source_name": "binance-crypto-airdrop",
            "campaign_type": "airdrop",
            "trust_status": "verified",
            "official_url_verified": True,
            **payload,
        })
        assert result["tier"] == "observation"
        assert result["is_published"] is False
