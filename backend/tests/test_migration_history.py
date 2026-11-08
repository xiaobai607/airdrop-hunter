from pathlib import Path

from alembic.config import Config
from alembic.script import ScriptDirectory


def test_personal_ledger_revision_is_the_single_migration_head() -> None:
    backend_dir = Path(__file__).resolve().parents[1]
    config = Config(str(backend_dir / "alembic.ini"))
    scripts = ScriptDirectory.from_config(config)

    assert scripts.get_current_head() == "0002_personal_ledger"
    bootstrap = scripts.get_revision("0001_bootstrap")
    ledger = scripts.get_revision("0002_personal_ledger")
    assert bootstrap is not None
    assert bootstrap.down_revision is None
    assert ledger is not None
    assert ledger.down_revision == "0001_bootstrap"
