from pathlib import Path

from alembic.config import Config
from alembic.script import ScriptDirectory


def test_wallet_network_identity_is_the_single_migration_head() -> None:
    backend_dir = Path(__file__).resolve().parents[1]
    config = Config(str(backend_dir / "alembic.ini"))
    scripts = ScriptDirectory.from_config(config)

    assert scripts.get_current_head() == "0002_wallet_network_identity"
    bootstrap = scripts.get_revision("0001_bootstrap")
    network_identity = scripts.get_revision("0002_wallet_network_identity")
    assert bootstrap is not None
    assert bootstrap.down_revision is None
    assert network_identity is not None
    assert network_identity.down_revision == "0001_bootstrap"
