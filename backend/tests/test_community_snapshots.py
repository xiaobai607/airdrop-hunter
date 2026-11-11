import json
from datetime import datetime, timezone
from pathlib import Path

import pytest

from app.services.community_snapshot_schedule import due_windows
from app.services.community_snapshot_storage import LocalSnapshotPublisher
from app.services.community_snapshots import assemble_snapshot, _range_distribution


def test_due_windows_follow_asia_shanghai_schedule():
    assert due_windows(datetime(2026, 11, 9, 0, 0, tzinfo=timezone.utc)) == ["1h", "overall", "7d"]
    assert due_windows(datetime(2026, 11, 9, 22, 0, tzinfo=timezone.utc)) == ["1h", "3d"]
    assert due_windows(datetime(2026, 11, 9, 0, 5, tzinfo=timezone.utc)) == ["1h"]
    assert due_windows(datetime(2026, 11, 9, 1, 0, tzinfo=timezone.utc)) == ["1h"]


def test_snapshot_contains_money_ratings_and_catalog_metadata_only():
    now = datetime(2026, 11, 9, 8, 0, tzinfo=timezone.utc)
    snapshot = assemble_snapshot(
        "3d", now,
        [{"project_official_id": "linea", "name": "Linea", "chain": "Ethereum", "logo": "L",
          "description": "Layer 2", "website_url": "https://linea.build",
          "created_at": datetime(2026, 1, 1, tzinfo=timezone.utc), "tags": ["L2"], "gas_cost_usd": 2.5,
          "actual_reward_usd": 12.0, "gas_user_reported_usd": 0.5,
          "gas_transaction_linked_usd": 1.0, "gas_amount_verified_usd": 1.0}],
        [{"project_official_id": "linea", "score": 5, "rating_count": 2},
         {"project_official_id": "linea", "score": 3, "rating_count": 1}],
    )
    project = snapshot["projects"][0]
    assert snapshot["window"] == "3d"
    assert snapshot["metric_currency"] == "USD"
    assert snapshot["usd_values_are_valuations"] is True
    assert project["gas_cost_usd_valuation"] == 2.5
    assert project["actual_reward_usd_valuation"] == 12.0
    assert project["reward_usd_valuation"] == 12.0
    assert project["estimated_reward_usd_valuation"] == 0.0
    assert "net_result_usd_valuation" not in project
    assert snapshot["data_quality"] == "approximate_actual_and_estimated_rewards"
    assert project["gas_by_verification_tier"] == {
        "user_reported": 0.5, "transaction_linked": 1.0, "amount_verified": 1.0
    }
    asset_rows = [{"project_official_id": "linea", "asset_breakdown": [
        {"kind": "gas", "amount_native": "0.1", "symbol": "ETH", "decimals": 18,
         "usd_valuation": "250", "valuation_timestamp": "2026-11-09T08:00:00+00:00", "valuation_source": "coinpaprika"},
        {"kind": "gas", "amount_native": "1.5", "symbol": "BNB", "decimals": 18,
         "usd_valuation": "900", "valuation_timestamp": "2026-11-09T08:01:00+00:00", "valuation_source": "coinpaprika"},
        {"kind": "reward", "amount_native": "100", "symbol": "LINEA", "decimals": 18,
         "usd_valuation": "12", "valuation_timestamp": "2026-11-09T08:02:00+00:00", "valuation_source": "user_input"},
    ]}]
    token_snapshot = assemble_snapshot(
        "overall", datetime(2026, 11, 9, tzinfo=timezone.utc),
        [{"project_official_id": "linea", "name": "Linea", "chain": "EVM", "logo": "",
          "description": "", "website_url": "", "created_at": datetime(2026, 1, 1, tzinfo=timezone.utc),
          "tags": [], "gas_cost_usd": 2.5, "actual_reward_usd": 12.0}],
        [], asset_rows,
    )
    token_project = token_snapshot["projects"][0]
    assert [item["symbol"] for item in token_project["gas_by_token"]] == ["BNB", "ETH"]
    assert token_project["gas_by_token"][0]["amount_native"] == "1.5"
    assert token_project["gas_by_token"][1]["amount_native"] == "0.1"
    assert token_project["reward_by_token"][0]["symbol"] == "LINEA"
    assert project["average_rating"] == pytest.approx(13 / 3)
    assert project["rating_distribution"] == {"1": 0, "2": 0, "3": 1, "4": 0, "5": 2}
    assert project["created_at"] == "2026-01-01T00:00:00+00:00"
    assert "contribution_count" not in project
    assert "interaction_count" not in project
    assert "activity_24h" not in project
    assert project["tags"] == ["L2"]
    assert "wallet_address" not in project
    assert "tx_hash" not in project




def test_public_reward_uses_actual_when_confirmed_and_estimate_when_not():
    now = datetime(2026, 11, 9, 8, 0, tzinfo=timezone.utc)
    snapshot = assemble_snapshot(
        "overall",
        now,
        [
            {
                "project_official_id": "actual-project",
                "name": "Actual",
                "chain": "Base",
                "logo": "",
                "description": "",
                "website_url": "",
                "created_at": now,
                "tags": [],
                "gas_cost_usd": 2.0,
                "actual_reward_usd": 12.0,
                "estimated_reward_usd": 4.0,
                "reward_usd": 12.0,
            },
            {
                "project_official_id": "estimate-project",
                "name": "Estimate",
                "chain": "Arbitrum",
                "logo": "",
                "description": "",
                "website_url": "",
                "created_at": now,
                "tags": [],
                "gas_cost_usd": 1.0,
                "actual_reward_usd": 0.0,
                "estimated_reward_usd": 8.0,
                "reward_usd": 8.0,
            },
        ],
        [],
    )
    projects = {row["project_official_id"]: row for row in snapshot["projects"]}
    assert projects["actual-project"]["reward_usd_valuation"] == 12.0
    assert projects["actual-project"]["estimated_reward_usd_valuation"] == 4.0
    assert projects["estimate-project"]["reward_usd_valuation"] == 8.0
    assert snapshot["summary"]["reward_total_usd"] == 20.0


def test_range_distribution_uses_anonymous_quantile_buckets_without_individual_rows():
    distribution = _range_distribution([10, 20, 30, 40, 50, 60])
    assert sum(item["count"] for item in distribution) == 6
    assert sum(item["percentage"] for item in distribution) == pytest.approx(100.0)
    assert all("range_label" in item for item in distribution)
    assert all("wallet_address" not in item for item in distribution)


def test_snapshot_skips_malformed_native_amount_without_crashing():
    now = datetime(2026, 11, 9, 8, 0, tzinfo=timezone.utc)
    snapshot = assemble_snapshot(
        "overall",
        now,
        [{
            "project_official_id": "linea",
            "name": "Linea",
            "chain": "EVM",
            "logo": "",
            "description": "",
            "website_url": "",
            "created_at": now,
            "tags": [],
            "gas_cost_usd": 0,
            "actual_reward_usd": 0,
        }],
        [],
        [{
            "project_official_id": "linea",
            "asset_breakdown": [{
                "kind": "gas",
                "amount_native": "not-a-number",
                "symbol": "ETH",
                "decimals": 18,
                "usd_valuation": "1",
            }],
        }],
    )
    assert snapshot["projects"][0]["gas_by_token"] == []


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
