from fastapi.testclient import TestClient

from app.main import app


def test_candidate_review_endpoints_require_catalog_admin_token(monkeypatch):
    monkeypatch.setenv("COMMUNITY_CATALOG_ADMIN_TOKEN", "review-secret")
    candidate_id = "5d2b3f2c-1a1e-4d8d-9a8b-6f9f55a2c001"
    with TestClient(app) as client:
        assert client.get("/api/airdrops/discovery/candidates/review-queue").status_code == 403
        assert client.get(
            "/api/airdrops/discovery/candidates/review-queue",
            headers={"X-Catalog-Admin-Token": "wrong"},
        ).status_code == 403
        assert client.post(
            f"/api/airdrops/discovery/candidates/{candidate_id}/approve"
        ).status_code == 403
        assert client.post(
            f"/api/airdrops/discovery/candidates/{candidate_id}/reject",
            headers={"X-Catalog-Admin-Token": "wrong"},
        ).status_code == 403
