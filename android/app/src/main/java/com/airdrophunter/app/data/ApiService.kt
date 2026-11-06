package com.airdrophunter.app.data

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface ApiService {
    @GET("api/projects")
    suspend fun getProjects(): List<ProjectDto>

    @POST("api/projects")
    suspend fun createProject(@Body request: ProjectWriteRequest): ProjectDto

    @PUT("api/projects/{projectId}")
    suspend fun updateProject(
        @Path("projectId") projectId: String,
        @Body request: ProjectWriteRequest
    ): ProjectDto

    @DELETE("api/projects/{projectId}")
    suspend fun deleteProject(@Path("projectId") projectId: String)
}
