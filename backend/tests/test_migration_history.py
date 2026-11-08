from pathlib import Path

from alembic.config import Config
from alembic.script import ScriptDirectory


def test_bootstrap_revision_is_the_single_migration_head() -> None:
    backend_dir = Path(__file__).resolve().parents[1]
    config = Config(str(backend_dir / "alembic.ini"))
    scripts = ScriptDirectory.from_config(config)

    assert scripts.get_current_head() == "0001_bootstrap"
    revision = scripts.get_revision("0001_bootstrap")
    assert revision is not None
    assert revision.down_revision is None
