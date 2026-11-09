import pytest

from app.services.deployment_readiness import (
    https_base,
    validate_cache_headers,
    validate_manifest,
    validate_response_origin,
    validate_snapshot,
    validate_snapshot_privacy,
)


def test_readiness_requires_https_bases_without_credentials():
    assert https_base("https://api.example.test/", "API_BASE_URL") == "https://api.example.test"
    with pytest.raises(ValueError):
        https_base("http://api.example.test", "API_BASE_URL")
    with pytest.raises(ValueError):
        https_base("https://user:pass@api.example.test", "API_BASE_URL")


def test_readiness_manifest_requires_all_four_windows_on_same_cdn_host():
    manifest = {
        "schema_version": 1,
        "snapshots": {
            window: {
                "key": f"snapshots/{window}/v1.json",
                "version": "v1",
                "update_time": "2026-11-09T16:00:00+08:00",
            }
            for window in ("overall", "7d", "3d", "1h")
        },
    }
    urls = validate_manifest(manifest, "https://cdn.example.test")
    assert set(urls) == {"overall", "7d", "3d", "1h"}
    assert all(url.startswith("https://cdn.example.test/") for url in urls.values())

    manifest["snapshots"]["3d"]["url"] = "https://attacker.example/3d.json"
    with pytest.raises(ValueError):
        validate_manifest(manifest, "https://cdn.example.test")
    manifest["snapshots"]["3d"]["url"] = "https://cdn.example.test:8443/3d.json"
    with pytest.raises(ValueError):
        validate_manifest(manifest, "https://cdn.example.test")
    manifest["snapshots"]["3d"]["url"] = "https://cdn.example.test/3d.json?token=secret"
    with pytest.raises(ValueError):
        validate_manifest(manifest, "https://cdn.example.test")


def test_readiness_snapshot_requires_matching_window_and_money_metadata():
    payload = {
        "schema_version": 1,
        "window": "1h",
        "generated_at": "2026-11-09T08:00:00+00:00",
        "update_time": "2026-11-09T16:00:00+08:00",
        "metric_currency": "USD",
        "usd_values_are_valuations": True,
        "projects": [],
    }
    validate_snapshot(payload, "1h", "2026-11-09T16:00:00+08:00")
    with pytest.raises(ValueError):
        validate_snapshot(payload, "3d", "2026-11-09T16:00:00+08:00")


def test_readiness_requires_short_lived_revalidating_manifest_and_validators():
    validate_cache_headers(
        {"cache-control": "public, max-age=60, must-revalidate", "etag": '"abc123"'},
        manifest=True,
    )
    with pytest.raises(ValueError):
        validate_cache_headers({"cache-control": "public, max-age=3600"}, manifest=True)
    with pytest.raises(ValueError):
        validate_cache_headers(
            {"cache-control": "public, max-age=60, must-revalidate"},
            manifest=True,
        )


def test_readiness_requires_immutable_long_lived_snapshot_cache():
    validate_cache_headers(
        {"cache-control": "public, max-age=31536000, immutable"},
        manifest=False,
    )
    with pytest.raises(ValueError):
        validate_cache_headers(
            {"cache-control": "public, max-age=60, must-revalidate"},
            manifest=False,
        )


def test_readiness_rejects_cross_origin_or_downgrade_redirects():
    validate_response_origin(
        "https://cdn.example.test/manifest.json",
        "https://cdn.example.test/manifest.json",
    )
    with pytest.raises(ValueError):
        validate_response_origin(
            "https://cdn.example.test/manifest.json",
            "https://attacker.example/manifest.json",
        )
    with pytest.raises(ValueError):
        validate_response_origin(
            "https://cdn.example.test/manifest.json",
            "http://cdn.example.test/manifest.json",
        )


def test_readiness_rejects_private_wallet_and_transaction_fields_recursively():
    validate_snapshot_privacy({
        "projects": [{"project_id": "linea", "gas_by_token": [{"symbol": "ETH"}]}],
    })
    with pytest.raises(ValueError):
        validate_snapshot_privacy({
            "projects": [{"stats": {"wallet_address": "0xprivate"}}],
        })
    with pytest.raises(ValueError):
        validate_snapshot_privacy({
            "projects": [{"events": [{"transaction_hash": "0xprivate"}]}],
        })
    with pytest.raises(ValueError):
        validate_snapshot_privacy({
            "projects": [{"stats": {"user_id": "account-123"}}],
        })
    with pytest.raises(ValueError):
        validate_snapshot_privacy({
            "projects": [{"stats": {"account_identifier": "account-123"}}],
        })
    with pytest.raises(ValueError):
        validate_snapshot_privacy({
            "projects": [{"stats": {"userIdentifier": "user-123"}}],
        })
