import json
import os
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Protocol


class SnapshotPublisher(Protocol):
    def publish(self, snapshots: dict[str, dict]) -> dict:
        ...


def _json_bytes(value: dict) -> bytes:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode("utf-8")


def _version(payload: dict) -> str:
    generated = datetime.fromisoformat(payload["generated_at"].replace("Z", "+00:00"))
    return generated.astimezone(timezone.utc).strftime("%Y%m%dT%H%M%SZ")


class LocalSnapshotPublisher:
    """Atomic local publisher for development and automated tests; not a public CDN."""

    def __init__(self, root: str | Path):
        self.root = Path(root)

    def publish(self, snapshots: dict[str, dict]) -> dict:
        self.root.mkdir(parents=True, exist_ok=True)
        manifest_path = self.root / "manifest.json"
        manifest = json.loads(manifest_path.read_text("utf-8")) if manifest_path.exists() else {
            "schema_version": 1, "snapshots": {}
        }
        manifest.setdefault("snapshots", {})

        for window, payload in snapshots.items():
            version = _version(payload)
            key = f"snapshots/{window}/{version}.json"
            target = self.root / key
            target.parent.mkdir(parents=True, exist_ok=True)
            self._atomic_write(target, _json_bytes(payload))
            manifest["snapshots"][window] = {
                "key": key,
                "version": version,
                "update_time": payload["update_time"],
            }

        manifest["generated_at"] = datetime.now(timezone.utc).isoformat(timespec="seconds")
        self._atomic_write(manifest_path, _json_bytes(manifest))
        return manifest

    @staticmethod
    def _atomic_write(target: Path, content: bytes) -> None:
        target.parent.mkdir(parents=True, exist_ok=True)
        fd, temporary_name = tempfile.mkstemp(prefix=f".{target.name}.", dir=target.parent)
        try:
            with os.fdopen(fd, "wb") as handle:
                handle.write(content)
                handle.flush()
                os.fsync(handle.fileno())
            os.replace(temporary_name, target)
        finally:
            if os.path.exists(temporary_name):
                os.unlink(temporary_name)


class S3SnapshotPublisher:
    """S3-compatible publisher (Cloudflare R2 supported through its S3 endpoint)."""

    def __init__(self, bucket: str, public_base_url: str, client):
        self.bucket = bucket
        self.public_base_url = public_base_url.rstrip("/")
        self.client = client

    @classmethod
    def from_environment(cls):
        bucket = os.getenv("COMMUNITY_SNAPSHOT_S3_BUCKET", "").strip()
        endpoint = os.getenv("COMMUNITY_SNAPSHOT_S3_ENDPOINT_URL", "").strip()
        access_key = os.getenv("COMMUNITY_SNAPSHOT_S3_ACCESS_KEY_ID", "").strip()
        secret_key = os.getenv("COMMUNITY_SNAPSHOT_S3_SECRET_ACCESS_KEY", "").strip()
        public_base_url = os.getenv("COMMUNITY_SNAPSHOT_PUBLIC_BASE_URL", "").strip()
        if not all((bucket, endpoint, access_key, secret_key, public_base_url)):
            raise RuntimeError(
                "Static snapshot storage is not configured. Set the S3-compatible endpoint, "
                "bucket, access key, secret key, and public CDN base URL."
            )

        import boto3

        client = boto3.client(
            "s3",
            endpoint_url=endpoint,
            aws_access_key_id=access_key,
            aws_secret_access_key=secret_key,
            region_name=os.getenv("COMMUNITY_SNAPSHOT_S3_REGION", "auto"),
        )
        return cls(bucket=bucket, public_base_url=public_base_url, client=client)

    def _read_manifest(self) -> dict:
        try:
            response = self.client.get_object(Bucket=self.bucket, Key="manifest.json")
            return json.loads(response["Body"].read().decode("utf-8"))
        except Exception as exc:
            error_code = getattr(exc, "response", {}).get("Error", {}).get("Code")
            if error_code in {"NoSuchKey", "404", "NotFound"}:
                return {"schema_version": 1, "snapshots": {}}
            raise

    def publish(self, snapshots: dict[str, dict]) -> dict:
        manifest = self._read_manifest()
        manifest.setdefault("snapshots", {})
        versioned_objects = []

        for window, payload in snapshots.items():
            version = _version(payload)
            key = f"snapshots/{window}/{version}.json"
            self.client.put_object(
                Bucket=self.bucket,
                Key=key,
                Body=_json_bytes(payload),
                ContentType="application/json; charset=utf-8",
                CacheControl="public, max-age=31536000, immutable",
            )
            versioned_objects.append(key)
            manifest["snapshots"][window] = {
                "key": key,
                "url": f"{self.public_base_url}/{key}",
                "version": version,
                "update_time": payload["update_time"],
            }

        manifest["generated_at"] = datetime.now(timezone.utc).isoformat(timespec="seconds")
        # This pointer is the commit point: clients never see a partial snapshot set.
        self.client.put_object(
            Bucket=self.bucket,
            Key="manifest.json",
            Body=_json_bytes(manifest),
            ContentType="application/json; charset=utf-8",
            CacheControl="public, max-age=60, must-revalidate",
        )
        return manifest


def publisher_from_environment() -> SnapshotPublisher:
    if os.getenv("COMMUNITY_SNAPSHOT_S3_BUCKET", "").strip():
        return S3SnapshotPublisher.from_environment()
    if os.getenv("APP_ENV", "").lower() == "production" or os.getenv("RENDER", "").lower() == "true":
        raise RuntimeError("Production must configure a public S3-compatible snapshot destination.")
    return LocalSnapshotPublisher(os.getenv("COMMUNITY_SNAPSHOT_LOCAL_DIR", "/tmp/airdrop-hunter-snapshots"))
