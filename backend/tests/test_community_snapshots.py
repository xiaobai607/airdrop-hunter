import json
from datetime import datetime, timezone
from pathlib import Path

import pytest

from app.services.community_snapshot_schedule import due_windows
from app.services.community_snapshot_storage import LocalSnapshotPublisher
from app.services.community_snapshots import assemble_snapshot


def test_due_windows_follow_asia_shanghai_schedule():
    assert due_windows(datetime(2026, 11, 9, 0, 0, tzinfo=timezone.utc)) == ["1h", "overall", "7d"]
    assert due_windows(datetime(2026, 11, 9, 22, 0, tzinfo=timezone.utc)) == ["1h", "3d"]
    assert due_windows(datetime(2026, 11, 9, 0, 5, tzinfo=timezone.utc)) == ["1h"]
    assert due_windows(datetime(2026, 11, 9, 1, 0, tzinfo=timezone.utc)) == ["1h"]


def test_snapshot_uses_only_aggregate_rows_and_keeps_24h_heat_separate():
    now = datetime(2026, 11, 9, 8, 0, tzinfo=timezone.utc)
    snapshot = assemble_snapshot(
        "3d", now,
        [{"project_official_id": "linea", "name": "Linea", "chain": "Ethereum", "logo": "L",
          "description": "Layer 2", "website_url": "https://linea.build", "gas_cost_usd": 2.5,
          "actual_reward_usd": 12.0, "contribution_count": 2, "interaction_count": 5}],
        [{"project_official_id": "linea", "score": 5, "rating_count": 2},
         {"project_official_id": "linea", "score": 3, "rating_count": 1}],
        [{"project_official_id": "linea", "contribution_count": 1, "interaction_count": 2}],
    )
    project = snapshot["projects"][0]
    assert snapshot["window"] == "3d"
    assert snapshot["metric_currency"] == "USD"
    assert snapshot["usd_values_are_valuations"] is True
    assert project["gas_cost_usd_valuation"] == 2.5
    assert project["actual_reward_usd_valuation"] == 12.0
    assert project["net_result_usd_valuation"] == 9.5
    assert project["average_rating"] == pytest.approx(13 / 3)
    assert project["rating_distribution"] == {"1": 0, "2": 0, "3": 1, "4": 0, "5": 2}
    assert project["activity_24h"]["interaction_count"] == 2
    assert "wallet_address" not in project
    assert "tx_hash" not in project


def test_local_publisher_keeps_previous_manifest_entries_and_versions_files(tmp_path: Path):
    publisher = LocalSnapshotPublisher(tmp_path)
    first = {"generated_at": "2026-11-09T08:00:00+00:00", "update_time": "2026-11-09T16:00:00+08:00",
             "window": "overall", "projects": []}
    manifest = publisher.publish({"overall": first})
    assert manifest["snapshots"]["overall"]["version"] == "20261109T080000Z"
    snapshot_path = tmp_path / manifest["snapshots"]["overall"]["key"]
    assert json.loads(snapshot_path.read_text("utf-8"))["window"] == "overall"

    second = {"generated_at": "2026-11-09T08:05:00+00:00", "update_time": "2026-11-09T16:05:00+08:00",
              "window": "1h", "projects": []}
    updated = publisher.publish({"1h": second})
    assert set(updated["snapshots"]) == {"overall", "1h"}
    assert (tmp_path / "manifest.json").exists()
