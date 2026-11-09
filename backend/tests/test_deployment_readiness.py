import pytest

from app.services.deployment_readiness import (
    https_base,
    validate_manifest,
    validate_snapshot,
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
