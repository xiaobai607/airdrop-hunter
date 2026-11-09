package com.airdrophunter.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectsRepositoryTest {
    private class FakeLocal : ProjectsLocalDataSource {
        val guest = mutableMapOf<String, String>()
        val account = mutableMapOf<String, String>()
        override suspend fun read(key: String, guest: Boolean): String? =
            (if (guest) this.guest else account)[key]
        override suspend fun write(key: String, value: String, guest: Boolean) {
            (if (guest) this.guest else account)[key] = value
        }
    }

    private class FakeRemote : ProjectsRemoteDataSource {
        val projects = mutableListOf<ProjectDto>()
        val tasks = mutableMapOf<String, MutableList<TaskDto>>()
        var projectCreateCalls = 0
        override suspend fun getProjects() = projects.toList()
        override suspend fun createProject(request: ProjectWriteRequest): ProjectDto {
            projectCreateCalls++
            return ProjectDto("remote-${projectCreateCalls}", request.name, request.description, request.status)
                .also { projects.add(it) }
        }
        override suspend fun updateProject(projectId: String, request: ProjectWriteRequest): ProjectDto =
            projects.first { it.id == projectId }.copy(
                name = request.name,
                description = request.description,
                status = request.status,
                official_url = request.official_url,
                claim_url = request.claim_url,
                twitter_url = request.twitter_url,
                deadline = request.deadline,
                expected_income = request.expected_income,
                gas_cost = request.gas_cost,
                actual_income = request.actual_income
            ).also { updated ->
                projects[projects.indexOfFirst { it.id == projectId }] = updated
            }
        override suspend fun deleteProject(projectId: String) { projects.removeAll { it.id == projectId } }
        override suspend fun getTasks(projectId: String) = tasks[projectId].orEmpty().toList()
        override suspend fun createTask(projectId: String, request: TaskCreateRequest): TaskDto =
            TaskDto("task-${request.step_order}", projectId, request.task_name, false, request.step_order ?: 1)
                .also { tasks.getOrPut(projectId) { mutableListOf() }.add(it) }
        override suspend fun updateTask(projectId: String, taskId: String, request: TaskUpdateRequest): TaskDto =
            tasks.getValue(projectId).first { it.id == taskId }.let { current ->
                current.copy(
                    is_completed = request.is_completed ?: current.is_completed,
                    task_name = request.task_name ?: current.task_name
                )
            }.also { updated -> tasks.getValue(projectId)[tasks.getValue(projectId).indexOfFirst { it.id == taskId }] = updated }
        override suspend fun deleteTask(projectId: String, taskId: String) {
            tasks[projectId]?.removeAll { it.id == taskId }
        }
        override suspend fun checkWallet(address: String) = WalletCheckResponse(true, "ok")
        override suspend fun getClaimUrl(projectId: String, walletId: String) =
            ClaimUrlResponse("https://claim.example/?address=0xabc", "Project", "Wallet", "0xabc")
    }

    private fun project(id: String, name: String = id) =
        ProjectDto(id, name, "", "tracking")

    @Test fun authenticatedClaimUrlIsRetrievedThroughOwnedWalletEndpoint() {
        val repository = ProjectsRepository(FakeRemote(), FakeLocal())
        kotlinx.coroutines.runBlocking {
            val response = repository.getClaimUrl("project-1", "wallet-1")
            assertEquals("https://claim.example/?address=0xabc", response.url)
            assertEquals("0xabc", response.address)
        }
    }

    @Test fun guestProjectsStayLocalAndIncludePnlProjects() {
        val local = FakeLocal()
        val remote = FakeRemote()
        val repository = ProjectsRepository(remote, local)
        kotlinx.coroutines.runBlocking {
            local.write("pnl_projects", com.google.gson.Gson().toJson(listOf(project("pnl"))), true)
            val created = repository.createProject(
                ProjectWriteRequest("Guest project", "description", "tracking"), guest = true
            )
            assertEquals(2, created.size)
            assertTrue(created.any { it.id == "pnl" })
            assertTrue(created.any { it.name == "Guest project" })
            assertEquals(0, remote.projectCreateCalls)
            assertTrue(local.guest.containsKey("projects"))
            assertFalse(local.account.containsKey("projects"))
        }
    }

    @Test fun authenticatedRefreshUsesAccountCacheAndDoesNotTouchGuestCache() {
        val local = FakeLocal()
        val remote = FakeRemote().apply { projects.add(project("server")) }
        val repository = ProjectsRepository(remote, local)
        kotlinx.coroutines.runBlocking {
            val result = repository.refreshProjects()
            assertEquals(listOf("server"), result.map { it.id })
            assertTrue(local.account.containsKey("projects"))
            assertFalse(local.guest.containsKey("projects"))
        }
    }

    @Test fun guestTasksArePersistedLocallyAndOrdered() {
        val local = FakeLocal()
        val repository = ProjectsRepository(FakeRemote(), local)
        kotlinx.coroutines.runBlocking {
            val first = repository.addTask("p1", "first", true, emptyList())
            val second = repository.addTask("p1", "second", true, first)
            assertEquals(listOf(1, 2), second.map { it.step_order })
            assertEquals(2, repository.readCachedTasks("p1", true).size)
            assertFalse(local.account.containsKey("tasks:p1"))
        }
    }

    @Test fun taskFromAnotherProjectCannotBeCompletedOrDeletedInGuestCache() {
        val local = FakeLocal()
        val repository = ProjectsRepository(FakeRemote(), local)
        kotlinx.coroutines.runBlocking {
            val projectOneTask = TaskDto("task-p1", "p1", "p1 task", false, 1)
            val projectTwoTask = TaskDto("task-p2", "p2", "p2 task", false, 1)
            local.write("tasks:p1", com.google.gson.Gson().toJson(listOf(projectOneTask)), true)

            val completionError = runCatching {
                repository.setTaskCompleted("p1", projectTwoTask, true, true, listOf(projectOneTask))
            }.exceptionOrNull()
            val deletionError = runCatching {
                repository.deleteTask("p1", projectTwoTask, true, listOf(projectOneTask))
            }.exceptionOrNull()

            assertTrue(completionError is IllegalArgumentException)
            assertTrue(deletionError is IllegalArgumentException)
            assertEquals(listOf("task-p1"), repository.readCachedTasks("p1", true).map { it.id })
            assertFalse(local.guest.containsKey("tasks:p2"))
        }
    }

    @Test fun blankTaskNamesAreRejectedBeforePersistence() {
        val local = FakeLocal()
        val repository = ProjectsRepository(FakeRemote(), local)
        kotlinx.coroutines.runBlocking {
            val error = runCatching { repository.addTask("p1", "   ", true, emptyList()) }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
            assertFalse(local.guest.containsKey("tasks:p1"))
        }
    }

    @Test fun guestTaskCanBeRenamedAndPersistsLocally() {
        val local = FakeLocal()
        val repository = ProjectsRepository(FakeRemote(), local)
        kotlinx.coroutines.runBlocking {
            val added = repository.addTask("p1", "Original name", true, emptyList())
            val renamed = repository.renameTask("p1", added.single(), "  Updated task  ", true, added)
            assertEquals("Updated task", renamed.single().task_name)
            assertEquals("Updated task", repository.readCachedTasks("p1", true).single().task_name)
            assertFalse(local.account.containsKey("tasks:p1"))
        }
    }

    @Test fun authenticatedTaskRenameUsesRemoteAndKeepsCompletionState() {
        val local = FakeLocal()
        val remote = FakeRemote()
        val repository = ProjectsRepository(remote, local)
        kotlinx.coroutines.runBlocking {
            val original = TaskDto("task-1", "p1", "Original", true, 1)
            remote.tasks["p1"] = mutableListOf(original)
            val renamed = repository.renameTask("p1", original, "Updated", false, listOf(original))
            assertEquals("Updated", renamed.single().task_name)
            assertTrue(renamed.single().is_completed)
            assertTrue(local.account.containsKey("tasks:p1"))
        }
    }


    @Test fun guestProjectEditPreservesAndUpdatesAllFieldsLocally() {
        val local = FakeLocal()
        val remote = FakeRemote()
        val repository = ProjectsRepository(remote, local)
        kotlinx.coroutines.runBlocking {
            val original = ProjectDto(
                id = "local-1", name = "Old", description = "Old details", status = "tracking",
                official_url = "https://old.example", claim_url = "https://old.example/claim",
                twitter_url = "https://x.com/old", deadline = "2026-12-01T00:00:00Z",
                expected_income = 10.0, gas_cost = 2.0, actual_income = 7.0
            )
            local.write("projects", com.google.gson.Gson().toJson(listOf(original)), true)
            val request = ProjectWriteRequest(
                name = "  New name ", description = " Updated details ", status = "completed",
                official_url = "https://new.example", claim_url = "https://new.example/claim",
                twitter_url = "https://x.com/new", deadline = "2027-01-01T00:00:00Z",
                expected_income = 20.0, gas_cost = 4.0, actual_income = 18.0
            )
            val updated = repository.updateProject(original, request, true).single()
            assertEquals("New name", updated.name)
            assertEquals("Updated details", updated.description)
            assertEquals("completed", updated.status)
            assertEquals("https://x.com/new", updated.twitter_url)
            assertEquals(20.0, updated.expected_income, 0.0)
            assertEquals(4.0, updated.gas_cost, 0.0)
            assertEquals(18.0, updated.actual_income, 0.0)
            assertTrue(remote.projects.isEmpty())
        }
    }

    @Test fun guestProjectDeleteRemovesLocalProjectWithoutCallingServer() {
        val local = FakeLocal()
        val remote = FakeRemote()
        val repository = ProjectsRepository(remote, local)
        kotlinx.coroutines.runBlocking {
            val original = project("local-delete")
            local.write("projects", com.google.gson.Gson().toJson(listOf(original)), true)
            val remaining = repository.deleteProject(original, true)
            assertTrue(remaining.isEmpty())
            assertTrue(repository.readCachedProjects(true).isEmpty())
            assertTrue(remote.projects.isEmpty())
        }
    }

    @Test fun authenticatedProjectEditUsesOwnedRemoteApiAndRefreshesAccountCache() {
        val local = FakeLocal()
        val remote = FakeRemote().apply { projects.add(project("server-1", "Before")) }
        val repository = ProjectsRepository(remote, local)
        kotlinx.coroutines.runBlocking {
            val original = remote.projects.single()
            val request = ProjectWriteRequest("After", "details", "completed")
            val updated = repository.updateProject(original, request, false)
            assertEquals("After", updated.single().name)
            assertEquals("completed", updated.single().status)
            assertTrue(local.account.containsKey("projects"))
            assertFalse(local.guest.containsKey("projects"))
        }
    }

}
