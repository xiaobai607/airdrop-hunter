from fastapi.testclient import TestClient

from app.main import app


def test_community_catalog_is_dynamic_and_public_listing_hides_inactive_entries():
    with TestClient(app) as client:
        response = client.get("/api/community/catalog")
        assert response.status_code == 200
        projects = response.json()
        assert {item["project_official_id"] for item in projects} >= {
            "linea", "scroll", "zksync", "starknet", "base", "arbitrum", "optimism"
        }
        assert all("website_url" in item and "tags" in item for item in projects)
        assert all("total_actual_reward_usd" not in item for item in projects)


def test_catalog_admin_can_create_update_and_deactivate(monkeypatch):
    monkeypatch.setenv("COMMUNITY_CATALOG_ADMIN_TOKEN", "catalog-test-secret")
    headers = {"X-Catalog-Admin-Token": "catalog-test-secret"}
    payload = {
        "project_official_id": "new-l2-project",
        "name": "New L2 Project",
        "chain": "Ethereum L2",
        "logo": "N",
        "description": "Curated project metadata",
        "tags": ["L2", " 零撸 ", "L2"],
        "website_url": "https://example.org",
        "is_active": True,
        "sort_order": 5,
    }
    with TestClient(app) as client:
        created = client.post("/api/community/catalog", headers=headers, json=payload)
        assert created.status_code == 201
        assert created.json()["tags"] == ["L2", "零撸"]
        assert any(p["project_official_id"] == "new-l2-project"
                   for p in client.get("/api/community/catalog").json())

        updated_payload = {**payload, "name": "Updated L2 Project", "is_active": False}
        updated = client.put("/api/community/catalog/new-l2-project", headers=headers, json=updated_payload)
        assert updated.status_code == 200
        assert updated.json()["name"] == "Updated L2 Project"
        assert all(p["project_official_id"] != "new-l2-project"
                   for p in client.get("/api/community/catalog").json())

        assert client.delete("/api/community/catalog/new-l2-project", headers=headers).status_code == 204


def test_catalog_admin_and_input_validation(monkeypatch):
    monkeypatch.setenv("COMMUNITY_CATALOG_ADMIN_TOKEN", "catalog-test-secret")
    with TestClient(app) as client:
        payload = {
            "project_official_id": "bad id",
            "name": "Bad Project",
            "website_url": "http://example.org",
            "is_active": True,
        }
        assert client.post("/api/community/catalog", json=payload).status_code == 403
        headers = {"X-Catalog-Admin-Token": "catalog-test-secret"}
        assert client.post("/api/community/catalog", headers=headers, json=payload).status_code == 422
        payload["project_official_id"] = "bad-project"
        assert client.post("/api/community/catalog", headers=headers, json=payload).status_code == 422
        assert client.get("/api/community/catalog").status_code == 200
