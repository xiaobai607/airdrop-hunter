from uuid import uuid4

from fastapi.testclient import TestClient

from app.main import app


def register(client):
    username = f"prj{uuid4().hex[:12]}"
    response = client.post("/api/auth/register", json={
        "username": username,
        "password": "strong-password-123",
    })
    assert response.status_code == 201
    return {"Authorization": f"Bearer {response.json()['access_token']}"}


def create_project(client, headers, name="Project"):
    response = client.post("/api/projects", headers=headers, json={"name": name})
    assert response.status_code == 201
    return response.json()


def test_project_task_lifecycle_is_owned_and_validated():
    with TestClient(app) as client:
        alice, bob = register(client), register(client)
        project = create_project(client, alice)
        project_id = project["id"]
        endpoint = f"/api/projects/{project_id}/tasks"

        created = client.post(endpoint, headers=alice, json={"task_name": "  First task  "})
        assert created.status_code == 201
        task = created.json()
        assert task["task_name"] == "First task"
        assert task["step_order"] == 1

        listed = client.get(endpoint, headers=alice)
        assert listed.status_code == 200
        assert [row["id"] for row in listed.json()] == [task["id"]]

        completed = client.patch(f"{endpoint}/{task['id']}", headers=alice, json={"is_completed": True})
        assert completed.status_code == 200
        assert completed.json()["is_completed"] is True

        renamed = client.patch(f"{endpoint}/{task['id']}", headers=alice, json={"task_name": "Renamed"})
        assert renamed.status_code == 200
        assert renamed.json()["task_name"] == "Renamed"
        assert renamed.json()["is_completed"] is True

        assert client.patch(f"{endpoint}/{task['id']}", headers=alice, json={"task_name": " "}).status_code == 422
        assert client.patch(f"{endpoint}/{task['id']}", headers=alice, json={"task_name": "x" * 241}).status_code == 422
        assert client.patch(f"{endpoint}/{task['id']}", headers=alice, json={}).status_code == 422
        assert client.get(endpoint, headers=bob).status_code == 404
        assert client.patch(f"{endpoint}/{task['id']}", headers=bob, json={"is_completed": False}).status_code == 404
        assert client.delete(f"{endpoint}/{task['id']}", headers=bob).status_code == 404

        deleted = client.delete(f"{endpoint}/{task['id']}", headers=alice)
        assert deleted.status_code == 204
        assert client.get(endpoint, headers=alice).json() == []


def test_project_deletion_removes_wallet_assignment_and_orphans_no_tasks():
    with TestClient(app) as client:
        headers = register(client)
        project = create_project(client, headers, "Delete lifecycle")
        project_id = project["id"]
        wallet = client.post("/api/wallets", headers=headers, json={
            "wallet_name": "Project wallet",
            "chain": "EVM",
            "address": "0x" + "a" * 40,
        })
        assert wallet.status_code == 201
        task_endpoint = f"/api/projects/{project_id}/tasks"
        task = client.post(task_endpoint, headers=headers, json={"task_name": "Pending step"})
        assert task.status_code == 201
        assignment_endpoint = f"/api/project-wallet-assignments/{project_id}"
        saved = client.put(assignment_endpoint, headers=headers, json={"wallet_ids": [wallet.json()["id"]]})
        assert saved.status_code == 200

        deleted = client.delete(f"/api/projects/{project_id}", headers=headers)
        assert deleted.status_code == 204
        assert client.get(task_endpoint, headers=headers).status_code == 404
        assert client.get(assignment_endpoint, headers=headers).status_code == 404


def test_project_write_links_require_https_without_credentials():
    with TestClient(app) as client:
        headers = register(client)
        invalid_payloads = [
            {"name": "Bad official", "official_url": "http://example.com"},
            {"name": "Bad claim", "claim_url": "https://user:pass@example.com/claim"},
            {"name": "Bad social", "twitter_url": "https://example.com:99999/profile"},
        ]
        for payload in invalid_payloads:
            response = client.post("/api/projects", headers=headers, json=payload)
            assert response.status_code == 422

        valid = client.post("/api/projects", headers=headers, json={
            "name": "Valid",
            "official_url": " https://example.com ",
            "claim_url": "https://example.com/claim",
            "twitter_url": "https://social.example/profile",
        })
        assert valid.status_code == 201
        assert valid.json()["official_url"] == "https://example.com"
