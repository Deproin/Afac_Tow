package com.example.api

import com.example.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Path
import java.util.concurrent.TimeUnit
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

// --- Simple Moshi-compatible API Data Classes ---
data class Part(val text: String? = null)
data class Content(val parts: List<Part>)
data class GenerateContentRequest(val contents: List<Content>)

data class Candidate(val content: Content)
data class GenerateContentResponse(val candidates: List<Candidate>?)

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }
}

object GeminiHelper {
    suspend fun generateContent(prompt: String, customKey: String = ""): String {
        val apiKey = customKey.ifEmpty { BuildConfig.GEMINI_API_KEY }
        if (apiKey.isEmpty()) {
            return "عذراً، مفتاح الذكاء الاصطناعي غير متوفر."
        }

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt))))
        )

        val modelsToTry = listOf(
            "gemini-3.5-flash",
            "gemini-2.5-flash",
            "gemini-1.5-flash",
            "gemini-2.0-flash"
        )



        var lastError: Exception? = null

        for (model in modelsToTry) {
            try {
                val response = RetrofitClient.service.generateContent(model, apiKey, request)
                val textResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (textResponse != null) {
                    return textResponse
                }
            } catch (e: Exception) {
                lastError = e
                val errorMsg = e.localizedMessage ?: e.message ?: ""
                if (errorMsg.contains("API key", ignoreCase = true) || errorMsg.contains("403") || errorMsg.contains("401") || errorMsg.contains("API_KEY_INVALID")) {
                    return "حدث خطأ في صلاحية مفتاح API الذكاء الاصطناعي: يرجى التحقق من صحة مفتاح GEMINI_API_KEY الذي أدخلته."
                }
            }
        }

        val finalErrorMsg = lastError?.localizedMessage ?: lastError?.message ?: "خطأ غير معروف"
        return "فشل الاتصال بجميع نماذج الذكاء الاصطناعي. يرجى التأكد من اتصال الإنترنت وصلاحية المفتاح المالي الخاص بك. (تفاصيل الخطأ الأخير: $finalErrorMsg)"
    }
}
