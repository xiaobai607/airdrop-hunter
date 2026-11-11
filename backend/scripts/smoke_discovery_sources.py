"""Read-only live smoke test for configured discovery adapters.

This command calls public providers and prints counts only. It does not open a
database session, persist observations, publish campaigns, or alter app data.
"""
from __future__ import annotations

import asyncio
import json
import os
from collections.abc import Awaitable, Callable
from typing import Any

import httpx

from app.services.airdrop_hub_sync import (
    _fetch_binance_observations,
    _fetch_coingecko_observations,
    _fetch_dexscreener_observations,
    _fetch_github_observations,
)


Adapter = Callable[[httpx.AsyncClient], Awaitable[list[dict[str, Any]]]]


async def main() -> int:
    adapters: list[tuple[str, Adapter, bool]] = [
        ("binance-announcements", _fetch_binance_observations, True),
        ("dexscreener-market-leads", _fetch_dexscreener_observations, True),
        (
            "coingecko-airdrop-category",
            _fetch_coingecko_observations,
            bool(os.getenv("COINGECKO_DEMO_API_KEY", "").strip()),
        ),
        (
            "github-raw-approved-lists",
            _fetch_github_observations,
            bool(os.getenv("AIRDROP_GITHUB_RAW_SOURCE_APPROVALS", "").strip()),
        ),
    ]

    succeeded = 0
    attempted = 0
    async with httpx.AsyncClient(
        timeout=httpx.Timeout(30.0),
        headers={
            "User-Agent": "AirdropHunter-Discovery-SmokeTest/1.0",
            "Accept": "application/json",
        },
    ) as client:
        for source_name, adapter, configured in adapters:
            if not configured:
                print(f"{source_name}: SKIPPED (optional configuration absent)")
                continue

            attempted += 1
            try:
                records = await adapter(client)
                count = len(records)
                if count:
                    succeeded += 1
                    samples = [
                        str(row.get("name") or row.get("title") or "")[:100]
                        for row in records[:3]
                    ]
                    print(
                        f"{source_name}: OK count={count} "
                        f"samples={json.dumps(samples, ensure_ascii=False)}"
                    )
                else:
                    print(f"{source_name}: EMPTY count=0")
            except Exception as exc:  # provider failures must not hide other results
                # Include bounded exception details so HTTP status/empty-result causes
                # are diagnosable from Actions without exposing response bodies.
                detail = " ".join(str(exc).split())[:400]
                print(
                    f"{source_name}: FAILED error_type={type(exc).__name__}"
                    + (f" detail={detail}" if detail else "")
                )

    print(f"SUMMARY: successful_sources={succeeded} attempted_sources={attempted}")
    # A smoke test is useful only if at least one configured provider returned
    # parseable records. Optional unconfigured providers are not failures.
    return 0 if succeeded else 1


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
