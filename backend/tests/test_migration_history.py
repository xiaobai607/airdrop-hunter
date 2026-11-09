from pathlib import Path

from alembic.config import Config
from alembic.script import ScriptDirectory


def test_migration_history_is_a_single_linear_chain() -> None:
    backend_dir = Path(__file__).resolve().parents[1]
    config = Config(str(backend_dir / "alembic.ini"))
    scripts = ScriptDirectory.from_config(config)

    assert scripts.get_current_head() == "0010_airdrop_discovery_candidates"
    bootstrap = scripts.get_revision("0001_bootstrap")
    ledger = scripts.get_revision("0002_personal_ledger")
    nullable_reward = scripts.get_revision("0003_nullable_expected_reward")
    community = scripts.get_revision("0004_community_contributions")
    anonymize = scripts.get_revision("0005_anonymize_contributions")
    ratings = scripts.get_revision("0006_community_ratings")
    wallet_metadata = scripts.get_revision("0007_wallet_metadata")
    project_wallet_assignments = scripts.get_revision("0008_project_wallet_assignments")
    dynamic_catalog = scripts.get_revision("0009_dynamic_community_catalog")
    discovery_candidates = scripts.get_revision("0010_airdrop_discovery_candidates")
    assert bootstrap is not None and bootstrap.down_revision is None
    assert ledger is not None and ledger.down_revision == "0001_bootstrap"
    assert nullable_reward is not None and nullable_reward.down_revision == "0002_personal_ledger"
    assert community is not None and community.down_revision == "0003_nullable_expected_reward"
    assert anonymize is not None and anonymize.down_revision == "0004_community_contributions"
    assert ratings is not None and ratings.down_revision == "0005_anonymize_contributions"
    assert wallet_metadata is not None and wallet_metadata.down_revision == "0006_community_ratings"
    assert project_wallet_assignments is not None and project_wallet_assignments.down_revision == "0007_wallet_metadata"
    assert dynamic_catalog is not None and dynamic_catalog.down_revision == "0008_project_wallet_assignments"
    assert discovery_candidates is not None and discovery_candidates.down_revision == "0009_dynamic_community_catalog"
