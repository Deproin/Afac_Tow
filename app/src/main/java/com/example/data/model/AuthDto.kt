package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AuthRequestDto(
    @Json(name = "email") val email: String,
    @Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class AuthResponseDto(
    @Json(name = "access_token") val accessToken: String?,
    @Json(name = "token_type") val tokenType: String?,
    @Json(name = "expires_in") val expiresIn: Int?,
    @Json(name = "refresh_token") val refreshToken: String?,
    @Json(name = "user") val user: AuthUserDto?
)

@JsonClass(generateAdapter = true)
data class AuthUserDto(
    @Json(name = "id") val id: String,
    @Json(name = "email") val email: String?
)
