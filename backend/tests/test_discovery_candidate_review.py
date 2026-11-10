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
