from uuid import uuid4

from fastapi.testclient import TestClient

from app.main import app


def test_candidate_review_endpoints_require_catalog_admin_token(monkeypatch):
    monkeypatch.setenv("COMMUNITY_CATALOG_ADMIN_TOKEN", "review-secret")
    candidate_id = "5d2b3f2c-1a1e-4d8d-9a8b-6f9f55a2c001"
    with TestClient(app) as client:
        assert client.get("/api/airdrops/discovery/candidates/review-queue").status_code == 403
        assert client.get("/api/airdrops/discovery/sources/status").status_code == 403
        assert client.get(
            "/api/airdrops/discovery/candidates/review-queue",
            headers={"X-Catalog-Admin-Token": "wrong"},
        ).status_code == 403
        assert client.get(
            "/api/airdrops/discovery/sources/status",
            headers={"X-Catalog-Admin-Token": "wrong"},
        ).status_code == 403
        status_response = client.get(
            "/api/airdrops/discovery/sources/status",
            headers={"X-Catalog-Admin-Token": "review-secret"},
        )
        assert status_response.status_code == 200
        assert isinstance(status_response.json(), list)
        assert client.post(
            f"/api/airdrops/discovery/candidates/{candidate_id}/approve"
        ).status_code == 403
        assert client.post(
            f"/api/airdrops/discovery/candidates/{candidate_id}/reject",
            headers={"X-Catalog-Admin-Token": "wrong"},
        ).status_code == 403


def _submit_short_link(client: TestClient, name: str) -> str:
    response = client.post(
        "/api/community/catalog/submit",
        json={"name": name, "url": f"https://bit.ly/{uuid4().hex}"},
    )
    assert response.status_code == 200
    assert response.json()["status"] == "pending_review"
    return response.json()["message"]


def test_admin_can_approve_candidate_into_catalog_and_prevent_repeat(monkeypatch):
    monkeypatch.setenv("COMMUNITY_CATALOG_ADMIN_TOKEN", "review-secret")
    headers = {"X-Catalog-Admin-Token": "review-secret"}
    with TestClient(app) as client:
        _submit_short_link(client, "Review Approval Project")
        queue = client.get(
            "/api/airdrops/discovery/candidates/review-queue",
            headers=headers,
        )
        assert queue.status_code == 200
        candidate = next(
            item for item in queue.json()
            if item["title"] == "Review Approval Project"
        )
        candidate_id = candidate["id"]

        approved = client.post(
            f"/api/airdrops/discovery/candidates/{candidate_id}/approve",
            headers=headers,
        )
        assert approved.status_code == 200
        assert approved.json()["status"] == "approved"
        project_id = approved.json()["project_official_id"]

        catalog = client.get("/api/community/catalog")
        assert any(item["project_official_id"] == project_id for item in catalog.json())

        repeated = client.post(
            f"/api/airdrops/discovery/candidates/{candidate_id}/approve",
            headers=headers,
        )
        assert repeated.status_code == 409

        reviewed = client.get(
            "/api/airdrops/discovery/candidates/review-queue?status=approved",
            headers=headers,
        )
        saved = next(item for item in reviewed.json() if item["id"] == candidate_id)
        assert saved["review_status"] == "approved"
        assert saved["reviewed_catalog_id"] == project_id
        assert saved["reviewed_at"] is not None


def test_admin_can_reject_candidate_and_reject_is_final(monkeypatch):
    monkeypatch.setenv("COMMUNITY_CATALOG_ADMIN_TOKEN", "review-secret")
    headers = {"X-Catalog-Admin-Token": "review-secret"}
    with TestClient(app) as client:
        _submit_short_link(client, "Review Rejection Project")
        queue = client.get(
            "/api/airdrops/discovery/candidates/review-queue",
            headers=headers,
        )
        candidate = next(
            item for item in queue.json()
            if item["title"] == "Review Rejection Project"
        )
        candidate_id = candidate["id"]

        rejected = client.post(
            f"/api/airdrops/discovery/candidates/{candidate_id}/reject",
            headers=headers,
        )
        assert rejected.status_code == 200
        assert rejected.json()["status"] == "rejected"

        repeated = client.post(
            f"/api/airdrops/discovery/candidates/{candidate_id}/reject",
            headers=headers,
        )
        assert repeated.status_code == 409

        reviewed = client.get(
            "/api/airdrops/discovery/candidates/review-queue?status=rejected",
            headers=headers,
        )
        saved = next(item for item in reviewed.json() if item["id"] == candidate_id)
        assert saved["review_status"] == "rejected"
        assert saved["reviewed_at"] is not None



def test_identity_group_endpoint_returns_complete_campaign_members_with_pagination(monkeypatch):
    from datetime import datetime, timezone
    from app.database import AsyncSessionLocal, get_db
    from app.models import AirdropDiscoveryCandidate

    monkeypatch.setenv("COMMUNITY_CATALOG_ADMIN_TOKEN", "review-secret")
    headers = {"X-Catalog-Admin-Token": "review-secret"}
    campaign_key = "a" * 64
    project_key = "b" * 64
    seeded = False

    async def seed(db):
        now = datetime.now(timezone.utc)
        for index in range(3):
            db.add(AirdropDiscoveryCandidate(
                source_key=f"group-test-{uuid4().hex}",
                source_name=f"source-{index}",
                source_url=f"https://source-{index}.example/",
                title=f"Campaign evidence {index}",
                item_url=f"https://campaign.example/activity/{index}",
                summary="evidence",
                first_seen_at=now,
                last_seen_at=now,
                review_status="pending",
                filter_status="keep",
                filter_reasons=[],
                filter_metadata={},
                project_identity_key=project_key,
                campaign_identity_key=campaign_key,
                identity_status="same_project_same_campaign",
                identity_evidence={
                    "identity_group_type": "campaign",
                    "identity_group_key": campaign_key,
                },
            ))
        # Same project, different campaign must not enter this campaign group.
        db.add(AirdropDiscoveryCandidate(
            source_key=f"group-test-{uuid4().hex}",
            source_name="source-other",
            source_url="https://source-other.example/",
            title="Different campaign",
            item_url="https://campaign.example/activity/other",
            summary="separate activity",
            first_seen_at=now,
            last_seen_at=now,
            review_status="pending",
            filter_status="keep",
            filter_reasons=[],
            filter_metadata={},
            project_identity_key=project_key,
            campaign_identity_key="c" * 64,
            identity_status="same_project_distinct_campaign",
            identity_evidence={
                "identity_group_type": "campaign",
                "identity_group_key": "c" * 64,
            },
        ))
        await db.commit()

    async def override_db():
        nonlocal seeded
        async with AsyncSessionLocal() as db:
            if not seeded:
                await seed(db)
                seeded = True
            yield db

    app.dependency_overrides[get_db] = override_db
    try:
        with TestClient(app) as client:
            first = client.get(
                f"/api/airdrops/discovery/identity-groups/campaign/{campaign_key}",
                headers=headers,
                params={"limit": 2, "offset": 0},
            )
            assert first.status_code == 200, first.text
            payload = first.json()
            assert payload["total_count"] == 3
            assert payload["has_more"] is True
            assert payload["next_offset"] == 2
            assert len(payload["members"]) == 2
            assert all(item["identity_group_type"] == "campaign" for item in payload["members"])

            second = client.get(
                f"/api/airdrops/discovery/identity-groups/campaign/{campaign_key}",
                headers=headers,
                params={"limit": 2, "offset": 2},
            )
            assert second.status_code == 200
            assert second.json()["total_count"] == 3
            assert second.json()["has_more"] is False
            assert len(second.json()["members"]) == 1

            project_group = client.get(
                f"/api/airdrops/discovery/identity-groups/project/{project_key}",
                headers=headers,
            )
            assert project_group.status_code == 200
            assert project_group.json()["membership_scope"] == "same_project_parent"
            assert project_group.json()["total_count"] == 4
    finally:
        app.dependency_overrides.pop(get_db, None)


def test_identity_group_endpoint_rejects_weak_or_malformed_group_keys(monkeypatch):
    monkeypatch.setenv("COMMUNITY_CATALOG_ADMIN_TOKEN", "review-secret")
    with TestClient(app) as client:
        headers = {"X-Catalog-Admin-Token": "review-secret"}
        invalid_type = client.get(
            f"/api/airdrops/discovery/identity-groups/unresolved/{'a' * 64}",
            headers=headers,
        )
        invalid_key = client.get(
            "/api/airdrops/discovery/identity-groups/campaign/not-a-fingerprint",
            headers=headers,
        )
        assert invalid_type.status_code == 422
        assert invalid_key.status_code == 422
