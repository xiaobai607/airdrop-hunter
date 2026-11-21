from fastapi.testclient import TestClient

from app.main import app


def test_community_catalog_is_dynamic_and_includes_explicit_inactive_state():
    with TestClient(app) as client:
        response = client.get("/api/community/catalog")
        assert response.status_code == 200
        projects = response.json()
        assert {item["project_official_id"] for item in projects} >= {
            "linea", "scroll", "zksync", "starknet", "base", "arbitrum", "optimism"
        }
        assert all("website_url" in item and "tags" in item for item in projects)
        assert all("is_active" in item and "sort_order" in item for item in projects)
        assert all("supported_chains" in item and "participation_requirements" in item for item in projects)
        assert all("funding_requirement" in item and "campaign_stage" in item for item in projects)
        linea = next(item for item in projects if item["project_official_id"] == "linea")
        assert linea["supported_chains"] == ["linea"]
        assert linea["categories"] == ["layer2"]
        assert linea["funding_requirement"] == "unknown"
        assert linea["participation_requirements"] == []
        assert all(item["is_active"] is True for item in projects)
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
        "supported_chains": ["base"],
        "categories": ["layer2", "defi"],
        "participation_requirements": ["bridge", "swap"],
        "funding_requirement": "gas_only",
        "campaign_stage": "points_season",
        "reward_types": ["points"],
        "risk_flags": ["contract_approval"],
        "effort_level": "recurring",
        "criteria_source_url": "https://example.org/docs/eligibility",
        "metadata_note": "Eligibility metadata verified from official docs.",
    }
    with TestClient(app) as client:
        created = client.post("/api/community/catalog", headers=headers, json=payload)
        assert created.status_code == 201
        assert created.json()["tags"] == ["L2", "零撸"]
        assert created.json()["supported_chains"] == ["base"]
        assert created.json()["participation_requirements"] == ["bridge", "swap"]
        assert created.json()["funding_requirement"] == "gas_only"
        assert created.json()["campaign_stage"] == "points_season"
        assert any(p["project_official_id"] == "new-l2-project"
                   for p in client.get("/api/community/catalog").json())

        updated_payload = {**payload, "name": "Updated L2 Project", "is_active": False}
        updated = client.put("/api/community/catalog/new-l2-project", headers=headers, json=updated_payload)
        assert updated.status_code == 200
        assert updated.json()["name"] == "Updated L2 Project"
        directory_after_deactivation = client.get("/api/community/catalog").json()
        deactivated = next(p for p in directory_after_deactivation
                           if p["project_official_id"] == "new-l2-project")
        assert deactivated["is_active"] is False
        assert deactivated["name"] == "Updated L2 Project"

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
