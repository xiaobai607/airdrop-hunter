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
            projects.first { it.id == projectId }.copy(name = request.name).also { updated ->
                projects[projects.indexOfFirst { it.id == projectId }] = updated
            }
        override suspend fun deleteProject(projectId: String) { projects.removeAll { it.id == projectId } }
        override suspend fun getTasks(projectId: String) = tasks[projectId].orEmpty().toList()
        override suspend fun createTask(projectId: String, request: TaskCreateRequest): TaskDto =
            TaskDto("task-${request.step_order}", projectId, request.task_name, false, request.step_order ?: 1)
                .also { tasks.getOrPut(projectId) { mutableListOf() }.add(it) }
        override suspend fun updateTask(projectId: String, taskId: String, request: TaskUpdateRequest): TaskDto =
            tasks.getValue(projectId).first { it.id == taskId }.copy(is_completed = request.is_completed)
                .also { updated -> tasks.getValue(projectId)[tasks.getValue(projectId).indexOfFirst { it.id == taskId }] = updated }
        override suspend fun deleteTask(projectId: String, taskId: String) {
            tasks[projectId]?.removeAll { it.id == taskId }
        }
        override suspend fun checkWallet(address: String) = WalletCheckResponse(true, "ok")
    }

    private fun project(id: String, name: String = id) =
        ProjectDto(id, name, "", "tracking")

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
}
