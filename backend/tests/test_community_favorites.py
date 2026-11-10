from uuid import uuid4

from fastapi.testclient import TestClient

from app.main import app


def auth(client):
    username = f"fav{uuid4().hex[:12]}"
    response = client.post(
        "/api/auth/register",
        json={"username": username, "password": "strong-password-123"},
    )
    assert response.status_code == 201
    return {"Authorization": f"Bearer {response.json()['access_token']}"}


def test_community_favorites_are_authenticated_and_account_scoped():
    with TestClient(app) as client:
        alice, bob = auth(client), auth(client)
        endpoint = "/api/community/favorites"
        assert client.get(endpoint).status_code == 401
        assert client.put(endpoint + "/sync", json={"items": []}).status_code == 401
        assert client.get(endpoint, headers=alice).json() == []
        assert client.get(endpoint, headers=bob).json() == []

        payload = {"items": [{
            "project_official_id": "linea",
            "project_name": "Linea",
            "is_favorite": True,
            "updated_at": 100,
        }]}
        saved = client.put(endpoint + "/sync", headers=alice, json=payload)
        assert saved.status_code == 200
        assert saved.json() == payload["items"]
        assert client.get(endpoint, headers=bob).json() == []


def test_community_favorite_tombstones_and_stale_updates_are_preserved():
    with TestClient(app) as client:
        headers = auth(client)
        endpoint = "/api/community/favorites"
        favorite = {
            "project_official_id": "scroll",
            "project_name": "Scroll",
            "is_favorite": True,
            "updated_at": 200,
        }
        assert client.put(endpoint + "/sync", headers=headers, json={"items": [favorite]}).status_code == 200

        unfavorite = {**favorite, "is_favorite": False, "updated_at": 300}
        deleted = client.put(endpoint + "/sync", headers=headers, json={"items": [unfavorite]})
        assert deleted.status_code == 200
        assert deleted.json()[0]["is_favorite"] is False

        stale = {**favorite, "updated_at": 250}
        ignored = client.put(endpoint + "/sync", headers=headers, json={"items": [stale]})
        assert ignored.status_code == 200
        assert ignored.json()[0]["is_favorite"] is False
        assert ignored.json()[0]["updated_at"] == 300


def test_community_favorite_sync_rejects_invalid_and_duplicate_ids():
    with TestClient(app) as client:
        headers = auth(client)
        endpoint = "/api/community/favorites/sync"
        invalid = {"items": [{
            "project_official_id": "bad/id",
            "project_name": "Bad",
            "is_favorite": True,
            "updated_at": 1,
        }]}
        assert client.put(endpoint, headers=headers, json=invalid).status_code == 422
        duplicate = {"items": [
            {"project_official_id": "linea", "project_name": "Linea", "is_favorite": True, "updated_at": 1},
            {"project_official_id": "linea", "project_name": "Linea", "is_favorite": False, "updated_at": 2},
        ]}
        assert client.put(endpoint, headers=headers, json=duplicate).status_code == 422



def test_community_favorite_rejects_future_clock_skew_and_blank_names():
    import time

    with TestClient(app) as client:
        headers = auth(client)
        endpoint = "/api/community/favorites/sync"
        future = {
            "project_official_id": "future-project",
            "project_name": "Future Project",
            "is_favorite": True,
            "updated_at": int(time.time() * 1000) + 10 * 60 * 1000,
        }
        assert client.put(endpoint, headers=headers, json={"items": [future]}).status_code == 422

        blank_name = {**future, "project_official_id": "blank-project", "project_name": "   ", "updated_at": 100}
        assert client.put(endpoint, headers=headers, json={"items": [blank_name]}).status_code == 422
