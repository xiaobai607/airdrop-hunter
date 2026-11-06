package com.airdrophunter.app.data

import retrofit2.http.GET

interface ApiService {
    @GET("api/projects")
    suspend fun getProjects(): List<ProjectDto>
}
