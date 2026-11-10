"""Manual PostgreSQL backup/restore drill.

The restore target must be a separately provisioned, empty database whose name
ends in _restore_check. The script never drops or cleans the target database.
"""
from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
from sqlalchemy.engine import make_url


def _connection_options(raw: str, label: str) -> tuple[list[str], dict[str, str], tuple[str, int, str]]:
    normalized = raw.strip()
    if normalized.lower().startswith("postgres://"):
        normalized = "postgresql://" + normalized[len("postgres://"):]
    try:
        url = make_url(normalized)
    except Exception as exc:
        raise ValueError(f"{label} is not a valid PostgreSQL URL") from exc

    if url.drivername.startswith("postgresql+"):
        url = url.set(drivername="postgresql")
    if (
        url.drivername != "postgresql"
        or not url.host
        or not url.database
        or not url.username
    ):
        raise ValueError(f"{label} must include PostgreSQL host, database, and username")

    args = [
        "--host", url.host,
        "--port", str(url.port or 5432),
        "--username", url.username,
        "--dbname", url.database,
        "--no-password",
    ]
    env = os.environ.copy()
    if url.password is not None:
        env["PGPASSWORD"] = url.password
    sslmode = url.query.get("sslmode")
    if isinstance(sslmode, str) and sslmode:
        env["PGSSLMODE"] = sslmode
    return args, env, (url.host.lower(), url.port or 5432, url.database)


def _validate_restore_target(source: tuple[str, int, str], target: tuple[str, int, str]) -> None:
    if source == target:
        raise ValueError("Backup source and restore target must be different databases")
    if not target[2].lower().endswith("_restore_check"):
        raise ValueError("Restore target database name must end with _restore_check")


def _run(command: list[str], env: dict[str, str], label: str) -> str:
    result = subprocess.run(command, env=env, check=False, capture_output=True, text=True)
    if result.returncode != 0:
        # Do not print command arguments, database URLs, passwords, or raw server output.
        raise RuntimeError(f"{label} failed (exit code {result.returncode})")
    return result.stdout.strip()


def _query(connection_args: list[str], env: dict[str, str], sql: str) -> str:
    return _run(
        ["psql", *connection_args, "-X", "-A", "-t", "-v", "ON_ERROR_STOP=1", "-c", sql],
        env,
        "PostgreSQL verification query",
    )


def _assert_restore_target_empty(connection_args: list[str], env: dict[str, str]) -> None:
    """Refuse to restore into a database that already contains application objects."""
    object_count = _query(
        connection_args,
        env,
        "SELECT "
        "(SELECT count(*) FROM pg_class c "
        "JOIN pg_namespace n ON n.oid = c.relnamespace "
        "WHERE n.nspname NOT IN ('pg_catalog', 'information_schema') "
        "AND n.nspname NOT LIKE 'pg_toast%' "
        "AND c.relkind IN ('r', 'p', 'v', 'm', 'S', 'f')) + "
        "(SELECT count(*) FROM pg_proc p "
        "JOIN pg_namespace n ON n.oid = p.pronamespace "
        "WHERE n.nspname NOT IN ('pg_catalog', 'information_schema') "
        "AND n.nspname NOT LIKE 'pg_toast%') + "
        "(SELECT count(*) FROM pg_type t "
        "JOIN pg_namespace n ON n.oid = t.typnamespace "
        "WHERE n.nspname NOT IN ('pg_catalog', 'information_schema') "
        "AND n.nspname NOT LIKE 'pg_toast%' AND t.typtype <> 'p') + "
        "(SELECT count(*) FROM pg_namespace "
        "WHERE nspname NOT IN ('pg_catalog', 'information_schema', 'public') "
        "AND nspname NOT LIKE 'pg_toast%')",
    )
    try:
        count = int(object_count)
    except (TypeError, ValueError) as exc:
        raise RuntimeError("Could not verify that the restore target database is empty") from exc
    if count != 0:
        raise RuntimeError("Restore target database is not empty; no restore was attempted")


def _schema_signature(connection_args: list[str], env: dict[str, str]) -> tuple[str, tuple[str, ...]]:
    revision = _query(
        connection_args, env,
        "SELECT version_num FROM alembic_version ORDER BY version_num",
    )
    if not revision:
        raise RuntimeError("Database restore verification found no Alembic revision")
    tables = _query(
        connection_args, env,
        "SELECT quote_ident(schemaname) || '.' || quote_ident(tablename) "
        "FROM pg_tables WHERE schemaname = 'public' ORDER BY 1",
    )
    return revision, tuple(line for line in tables.splitlines() if line)


def verify_backup_restore(source_url: str, restore_url: str) -> dict[str, object]:
    source_args, source_env, source_identity = _connection_options(
        source_url, "BACKUP_SOURCE_DATABASE_URL"
    )
    restore_args, restore_env, restore_identity = _connection_options(
        restore_url, "BACKUP_RESTORE_DATABASE_URL"
    )
    _validate_restore_target(source_identity, restore_identity)

    for executable in ("pg_dump", "pg_restore", "psql"):
        if shutil.which(executable) is None:
            raise RuntimeError(f"Required PostgreSQL utility is missing: {executable}")

    # Check before creating the archive or writing anything to the restore target.
    _assert_restore_target_empty(restore_args, restore_env)

    with tempfile.TemporaryDirectory(prefix="airdrop-hunter-backup-check-") as temp_dir:
        archive = os.path.join(temp_dir, "database.dump")
        _run(
            [
                "pg_dump", *source_args,
                "--format=custom", "--no-owner", "--no-acl",
                "--file", archive,
            ],
            source_env,
            "PostgreSQL backup",
        )
        if not os.path.isfile(archive) or os.path.getsize(archive) == 0:
            raise RuntimeError("PostgreSQL backup archive is empty")

        # Deliberately do not use --clean or dropdb: a target that was not empty
        # was rejected before the source dump was created.
        _run(
            [
                "pg_restore", *restore_args,
                "--exit-on-error", "--no-owner", "--no-acl",
                archive,
            ],
            restore_env,
            "PostgreSQL restore",
        )

        source_revision, source_tables = _schema_signature(source_args, source_env)
        restored_revision, restored_tables = _schema_signature(restore_args, restore_env)
        if source_revision != restored_revision:
            raise RuntimeError("Restored database Alembic revision does not match the source")
        if source_tables != restored_tables:
            raise RuntimeError("Restored database public table set does not match the source")
        return {
            "alembic_revision": restored_revision,
            "public_table_count": len(restored_tables),
            "archive_bytes": os.path.getsize(archive),
        }


def main() -> None:
    source_url = os.getenv("BACKUP_SOURCE_DATABASE_URL", "")
    restore_url = os.getenv("BACKUP_RESTORE_DATABASE_URL", "")
    if not source_url or not restore_url:
        raise RuntimeError(
            "Configure BACKUP_SOURCE_DATABASE_URL and BACKUP_RESTORE_DATABASE_URL secrets"
        )
    result = verify_backup_restore(source_url, restore_url)
    print("OK: PostgreSQL backup archive created and restored successfully")
    print(f"OK: Alembic revision {result['alembic_revision']}")
    print(f"OK: {result['public_table_count']} public tables restored and verified")


if __name__ == "__main__":
    main()
