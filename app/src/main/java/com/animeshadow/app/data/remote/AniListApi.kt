package com.animeshadow.app.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

interface AniListApi {
    @POST("/")
    suspend fun page(@Body request: GqlRequest): PageResponse

    @POST("/")
    suspend fun media(@Body request: GqlRequest): MediaResponse

    @POST("/")
    suspend fun character(@Body request: GqlRequest): CharacterResponse

    @POST("/")
    suspend fun staff(@Body request: GqlRequest): StaffResponse

    @POST("/")
    suspend fun studio(@Body request: GqlRequest): StudioResponse
}
