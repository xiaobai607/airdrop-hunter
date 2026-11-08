from pathlib import Path

from alembic.config import Config
from alembic.script import ScriptDirectory


def test_community_contribution_is_the_single_migration_head() -> None:
    backend_dir = Path(__file__).resolve().parents[1]
    config = Config(str(backend_dir / "alembic.ini"))
    scripts = ScriptDirectory.from_config(config)

    assert scripts.get_current_head() == "0004_community_contributions"
    bootstrap = scripts.get_revision("0001_bootstrap")
    ledger = scripts.get_revision("0002_personal_ledger")
    nullable_reward = scripts.get_revision("0003_nullable_expected_reward")
    community = scripts.get_revision("0004_community_contributions")
    assert bootstrap is not None and bootstrap.down_revision is None
    assert ledger is not None and ledger.down_revision == "0001_bootstrap"
    assert nullable_reward is not None and nullable_reward.down_revision == "0002_personal_ledger"
    assert community is not None and community.down_revision == "0003_nullable_expected_reward"
