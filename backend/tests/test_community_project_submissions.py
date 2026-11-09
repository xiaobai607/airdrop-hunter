from fastapi.testclient import TestClient

from app.main import app


def test_public_submission_publishes_minimal_safe_project():
    with TestClient(app) as client:
        response = client.post("/api/community/catalog/submit", json={
            "name": "Free Source Project QA",
            "url": "https://example-project-qa.org/airdrop?utm_source=test#overview",
        })
        assert response.status_code == 200
        body = response.json()
        assert body["status"] == "published"
        project = body["existing_project"]
        assert project["name"] == "Free Source Project QA"
        assert project["website_url"] == "https://example-project-qa.org/airdrop"
        assert project["logo"] == ""
        assert project["tags"] == []
        assert project["chain"] == "Unknown"
        assert any(
            item["project_official_id"] == project["project_official_id"]
            for item in client.get("/api/community/catalog").json()
        )


def test_public_submission_detects_existing_project_by_name():
    with TestClient(app) as client:
        response = client.post("/api/community/catalog/submit", json={
            "name": "Linea",
            "url": "https://third-party-list.example/linea-airdrop",
        })
        assert response.status_code == 200
        body = response.json()
        assert body["status"] == "duplicate"
        assert body["existing_project"]["project_official_id"] == "linea"


def test_shortened_submission_url_is_held_for_review():
    with TestClient(app) as client:
        response = client.post("/api/community/catalog/submit", json={
            "name": "Short Link Project QA",
            "url": "https://bit.ly/airdrop-qa-123",
        })
        assert response.status_code == 200
        assert response.json()["status"] == "pending_review"
        candidates = client.get("/api/airdrops/discovery/candidates").json()
        assert any(item["title"] == "Short Link Project QA" for item in candidates)


def test_public_submission_rejects_unsafe_urls():
    with TestClient(app) as client:
        for url in (
            "http://example.org/project",
            "https://user:pass@example.org/project",
            "https://127.0.0.1/project",
            "https://localhost/project",
        ):
            response = client.post("/api/community/catalog/submit", json={
                "name": "Unsafe Project QA",
                "url": url,
            })
            assert response.status_code == 422


def test_distinct_chinese_project_names_are_not_treated_as_duplicates():
    with TestClient(app) as client:
        first = client.post("/api/community/catalog/submit", json={
            "name": "星河测试项目甲",
            "url": "https://galaxy-project-a.example/airdrop",
        })
        second = client.post("/api/community/catalog/submit", json={
            "name": "星河测试项目乙",
            "url": "https://galaxy-project-b.example/airdrop",
        })
        assert first.status_code == 200 and first.json()["status"] == "published"
        assert second.status_code == 200 and second.json()["status"] == "published"
