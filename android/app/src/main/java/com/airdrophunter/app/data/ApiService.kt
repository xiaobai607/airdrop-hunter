package com.airdrophunter.app.data
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
interface ApiService {
    @POST("api/auth/register") suspend fun register(@Body request: AuthRequest): AuthResponse
    @POST("api/auth/login") suspend fun login(@Body request: AuthRequest): AuthResponse
    @GET("api/sources") suspend fun getSources(): List<SourceDto>
    @GET("api/airdrops") suspend fun getAirdrops(): List<AirdropDto>
    @POST("api/airdrops") suspend fun createAirdrop(@Body request: AirdropWriteRequest): AirdropDto
    @GET("api/projects") suspend fun getProjects(): List<ProjectDto>
    @POST("api/projects") suspend fun createProject(@Body request: ProjectWriteRequest): ProjectDto
    @PUT("api/projects/{projectId}") suspend fun updateProject(@Path("projectId") projectId: String, @Body request: ProjectWriteRequest): ProjectDto
    @DELETE("api/projects/{projectId}") suspend fun deleteProject(@Path("projectId") projectId: String)
}
