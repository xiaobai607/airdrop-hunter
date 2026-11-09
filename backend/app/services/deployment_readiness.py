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


def validate_response_origin(requested_url: str, final_url: str) -> None:
    requested = urlsplit(requested_url)
    final = urlsplit(final_url)
    try:
        same_port = (requested.port or 443) == (final.port or 443)
    except ValueError:
        same_port = False
    if (
        final.scheme.lower() != "https"
        or requested.scheme.lower() != "https"
        or (requested.hostname or "").lower() != (final.hostname or "").lower()
        or not same_port
        or final.username is not None
        or final.password is not None
    ):
        raise ValueError("Readiness request redirected outside its configured HTTPS origin")


def validate_cache_headers(headers: dict[str, str], *, manifest: bool) -> None:
    cache_control = headers.get("cache-control", "")
    directives = {
        item.strip().lower().split("=", 1)[0]: item.strip().lower().split("=", 1)[1]
        if "=" in item else None
        for item in cache_control.split(",") if item.strip()
    }
    try:
        max_age = int(directives.get("max-age") or "")
    except (TypeError, ValueError):
        max_age = -1
    if "public" not in directives:
        raise ValueError("Community CDN response must be publicly cacheable")
    if manifest:
        if "must-revalidate" not in directives or not 0 <= max_age <= 300:
            raise ValueError("Community manifest must use a short public cache lifetime and must-revalidate")
        if not (headers.get("etag") or headers.get("last-modified")):
            raise ValueError("Community manifest must expose ETag or Last-Modified for conditional requests")
    elif "immutable" not in directives or max_age < 31536000:
        raise ValueError("Versioned community snapshots must be immutable and cached for at least one year")


def fetch_json(url: str) -> tuple[dict, dict[str, str]]:
    request = Request(url, headers={"Accept": "application/json", "User-Agent": "AirdropHunter-Readiness/1.0"})
    try:
        with urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            if response.status != 200:
                raise RuntimeError(f"Unexpected HTTP status {response.status} for {urlsplit(url).path}")
            validate_response_origin(url, response.geturl())
            content_type = response.headers.get_content_type()
            if content_type != "application/json":
                raise ValueError(f"Expected application/json at {urlsplit(url).path}")
            value = json.loads(response.read().decode("utf-8"))
            headers = {key.lower(): val for key, val in response.headers.items()}
    except (HTTPError, URLError, TimeoutError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise RuntimeError(f"Readiness request failed for {urlsplit(url).path}: {type(exc).__name__}") from exc
    if not isinstance(value, dict):
        raise ValueError(f"Expected a JSON object at {urlsplit(url).path}")
    return value, headers


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
        try:
            same_port = (parsed.port or 443) == (base.port or 443)
        except ValueError:
            same_port = False
        if (
            parsed.scheme.lower() != "https"
            or not parsed.hostname
            or parsed.hostname.lower() != (base.hostname or "").lower()
            or not same_port
            or parsed.username is not None
            or parsed.password is not None
            or parsed.query
            or parsed.fragment
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
    api, _ = fetch_json(api_base + "/health/ready")
    if api.get("status") != "ready" or not str(api.get("schema_revision") or "").strip():
        raise RuntimeError("API database/migration readiness check did not return ready")
    manifest, manifest_headers = fetch_json(cdn_base + "/manifest.json")
    validate_cache_headers(manifest_headers, manifest=True)
    urls = validate_manifest(manifest, cdn_base)
    for window, snapshot_url in urls.items():
        payload, snapshot_headers = fetch_json(snapshot_url)
        validate_cache_headers(snapshot_headers, manifest=False)
        reference = manifest["snapshots"][window]
        validate_snapshot(payload, window, str(reference["update_time"]))
    return [
        "API database and migration readiness: OK",
        "Static CDN manifest and four snapshot windows: OK",
        "Snapshot schema, update timestamps, HTTPS host and USD metadata: OK",
        "CDN manifest validators and immutable snapshot cache headers: OK",
    ]


def main() -> None:
    api_base = https_base(os.getenv("API_BASE_URL", ""), "API_BASE_URL")
    cdn_base = https_base(os.getenv("COMMUNITY_SNAPSHOT_BASE_URL", ""), "COMMUNITY_SNAPSHOT_BASE_URL")
    for result in run_readiness(api_base, cdn_base):
        print(f"OK: {result}")


