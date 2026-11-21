import asyncio
from datetime import datetime, timedelta, timezone

from app.services.discovery_source_status import record_discovery_source_status


class FakeSourceStatusDb:
    def __init__(self):
        self.row = None

    async def scalar(self, statement):
        return self.row

    def add(self, row):
        self.row = row


def test_source_status_preserves_last_success_after_failed_attempt_and_redacts_query():
    db = FakeSourceStatusDb()
    first_attempt = datetime(2026, 11, 9, 8, 0, tzinfo=timezone.utc)
    failed_attempt = first_attempt + timedelta(hours=1)
    source_url = "https://feeds.example/rss?token=do-not-store#section"

    first = asyncio.run(record_discovery_source_status(
        db, "ExampleFeed", source_url, first_attempt, "success", item_count=12,
    ))
    assert first.last_status == "success"
    assert first.last_item_count == 12
    assert first.last_success_at == first_attempt
    assert first.source_url == "https://feeds.example/rss"
    assert "do-not-store" not in first.source_url

    updated = asyncio.run(record_discovery_source_status(
        db, "ExampleFeed", source_url, failed_attempt, "failed", error="HTTPStatusError",
    ))
    assert updated is first
    assert updated.last_status == "failed"
    assert updated.last_attempt_at == failed_attempt
    assert updated.last_success_at == first_attempt
    assert updated.last_item_count == 12
    assert updated.last_error == "HTTPStatusError"


def test_source_status_distinguishes_empty_and_stale_without_erasing_last_success():
    db = FakeSourceStatusDb()
    first_attempt = datetime(2026, 11, 9, 8, 0, tzinfo=timezone.utc)
    first = asyncio.run(record_discovery_source_status(
        db, "ExampleFeed", "https://feeds.example/rss", first_attempt,
        "success", item_count=7,
    ))

    empty = asyncio.run(record_discovery_source_status(
        db, "ExampleFeed", "https://feeds.example/rss", first_attempt + timedelta(hours=1),
        "empty", error="source_empty_result",
    ))
    assert empty is first
    assert empty.last_status == "empty"
    assert empty.last_error == "source_empty_result"
    assert empty.last_success_at == first_attempt
    assert empty.last_item_count == 7

    stale = asyncio.run(record_discovery_source_status(
        db, "ExampleFeed", "https://feeds.example/rss", first_attempt + timedelta(hours=2),
        "stale", error="source_payload_stale_or_timestamp_invalid",
    ))
    assert stale.last_status == "stale"
    assert stale.last_error == "source_payload_stale_or_timestamp_invalid"
    assert stale.last_success_at == first_attempt
    assert stale.last_item_count == 7
