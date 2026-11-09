"""Manual production smoke test for the API and static community CDN.

No credentials are accepted or required. Configure API_BASE_URL and
COMMUNITY_SNAPSHOT_BASE_URL as repository variables before dispatching the workflow.
"""
from __future__ import annotations

import json
import os
from urllib.error import HTTPError, URLError
from urllib.parse import urljoin, urlsplit
from urllib.request import Request, urlopen

WINDOWS = ("overall", "7d", "3d", "1h")
TIMEOUT_SECONDS = 20


def https_base(value: str, label: str) -> str:
    parsed = urlsplit(value.strip())
    if (
        parsed.scheme.lower() != "https"
        or not parsed.hostname
        or parsed.username is not None
        or parsed.password is not None
        or parsed.query
        or parsed.fragment
    ):
        raise ValueError(f"{label} must be a public HTTPS base URL without credentials/query/fragment")
    return value.strip().rstrip("/")


def fetch_json(url: str) -> dict:
    request = Request(url, headers={"Accept": "application/json", "User-Agent": "AirdropHunter-Readiness/1.0"})
    try:
        with urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            if response.status != 200:
                raise RuntimeError(f"Unexpected HTTP status {response.status} for {urlsplit(url).path}")
            value = json.loads(response.read().decode("utf-8"))
    except (HTTPError, URLError, TimeoutError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise RuntimeError(f"Readiness request failed for {urlsplit(url).path}: {type(exc).__name__}") from exc
    if not isinstance(value, dict):
        raise ValueError(f"Expected a JSON object at {urlsplit(url).path}")
    return value


def validate_manifest(manifest: dict, cdn_base: str) -> dict[str, str]:
    if manifest.get("schema_version") != 1:
        raise ValueError("Community manifest schema_version must be 1")
    snapshots = manifest.get("snapshots")
    if not isinstance(snapshots, dict):
        raise ValueError("Community manifest snapshots must be an object")
    base = urlsplit(cdn_base)
    urls: dict[str, str] = {}
    for window in WINDOWS:
        reference = snapshots.get(window)
        if not isinstance(reference, dict):
            raise ValueError(f"Community manifest is missing the {window} snapshot")
        if not str(reference.get("version") or "").strip():
            raise ValueError(f"Community {window} snapshot has no version")
        if not str(reference.get("update_time") or "").strip():
            raise ValueError(f"Community {window} snapshot has no update_time")
        raw_url = str(reference.get("url") or "").strip()
        key = str(reference.get("key") or "").strip()
        snapshot_url = raw_url or (urljoin(cdn_base + "/", key) if key else "")
        parsed = urlsplit(snapshot_url)
        if (
            parsed.scheme.lower() != "https"
            or not parsed.hostname
            or parsed.hostname.lower() != (base.hostname or "").lower()
            or parsed.username is not None
            or parsed.password is not None
        ):
            raise ValueError(f"Community {window} snapshot URL must stay on the configured HTTPS CDN host")
        urls[window] = snapshot_url
    return urls


def validate_snapshot(payload: dict, window: str, expected_update_time: str) -> None:
    if payload.get("schema_version") != 1 or payload.get("window") != window:
        raise ValueError(f"Community {window} snapshot schema/window mismatch")
    if not str(payload.get("generated_at") or "").strip():
        raise ValueError(f"Community {window} snapshot has no generated_at")
    if payload.get("update_time") != expected_update_time:
        raise ValueError(f"Community {window} snapshot update_time does not match its manifest")
    if payload.get("metric_currency") != "USD" or payload.get("usd_values_are_valuations") is not True:
        raise ValueError(f"Community {window} snapshot money metadata is invalid")
    if not isinstance(payload.get("projects"), list):
        raise ValueError(f"Community {window} snapshot projects must be an array")


def run_readiness(api_base: str, cdn_base: str) -> list[str]:
    api = fetch_json(api_base + "/health/ready")
    if api.get("status") != "ready" or not str(api.get("schema_revision") or "").strip():
        raise RuntimeError("API database/migration readiness check did not return ready")
    manifest = fetch_json(cdn_base + "/manifest.json")
    urls = validate_manifest(manifest, cdn_base)
    for window, snapshot_url in urls.items():
        payload = fetch_json(snapshot_url)
        reference = manifest["snapshots"][window]
        validate_snapshot(payload, window, str(reference["update_time"]))
    return [
        "API database and migration readiness: OK",
        "Static CDN manifest and four snapshot windows: OK",
        "Snapshot schema, update timestamps, HTTPS host and USD metadata: OK",
    ]


def main() -> None:
    api_base = https_base(os.getenv("API_BASE_URL", ""), "API_BASE_URL")
    cdn_base = https_base(os.getenv("COMMUNITY_SNAPSHOT_BASE_URL", ""), "COMMUNITY_SNAPSHOT_BASE_URL")
    for result in run_readiness(api_base, cdn_base):
        print(f"OK: {result}")


if __name__ == "__main__":
    main()
