package com.example.util

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiUtility {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun generateSummary(textToSummarize: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            // Simulated response with very realistic markdown content in case the API key is not configured yet
            return@withContext """
                ### 📄 Document Summary (Simulation Mode)
                *You are running in Offline Simulation Mode because the API Key is not set in your Secrets panel.*

                Here is what a Gemini summary of your document would look like:
                
                *   **Core Objective**: The provided document details technical parameters, local-first wasm processing strategies, and AES-256 secure locker designs.
                *   **Performance Metrics**: WASM processing completes in under 800ms, ensuring immediate interactive feedback without server roundtrips.
                *   **Key Security Highlight**: AES-256 local-first cryptography prevents documents from ever leaving the user's phone, aligning with strict zero-trust standards.
                *   **Action Items**:
                    1. Update API Keys in AI Studio to switch from Simulation Mode to Live Mode.
                    2. Integrate local SQLite database caching for document metadata.
            """.trimIndent()
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

            val partObject = JSONObject().put("text", "Summarize this document content clearly, extracting key takeaways, actionable items, and important data points. Format beautifully with Markdown bullet points:\n\n$textToSummarize")
            val contentObject = JSONObject().put("parts", JSONArray().put(partObject))
            val contentsArray = JSONArray().put(contentObject)
            val requestBodyJson = JSONObject().put("contents", contentsArray)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestBodyJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext "Error calling Gemini API: ${response.code} ${response.message}"
                }

                val bodyString = response.body?.string() ?: return@withContext "Error: Received empty response from Gemini API"
                val jsonResponse = JSONObject(bodyString)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.optJSONObject("content")
                    if (content != null) {
                        val parts = content.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            return@withContext parts.getJSONObject(0).optString("text", "No text part found.")
                        }
                    }
                }
                "No summary generated."
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Exception while calling Gemini API: ${e.localizedMessage}"
        }
    }
}
