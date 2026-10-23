package com.bro.assistant.ai

import android.content.Context
import com.bro.assistant.security.SecretBox
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class WebGroundingResult(
    val sources: List<String>,
    val contextBlock: String
)

/**
 * Provides real-time Google Search grounding for Claude and other models.
 * Uses Gemini Search Grounding if a Gemini key is saved, or falls back to DuckDuckGo Instant Answer API.
 */
object WebGrounding {
    fun fetchSearchSnippets(
        context: Context,
        query: String,
        httpClient: OkHttpClient
    ): WebGroundingResult {
        val geminiKey = SecretBox.getKey(context, ProviderId.GEMINI.name)
        if (geminiKey.isNotBlank()) {
            try {
                val payload = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().put(JSONObject().put("text", "Provide brief factual web search notes for: $query")))
                    }))
                    put("tools", JSONArray().put(JSONObject().put("googleSearch", JSONObject())))
                }
                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$geminiKey"
                val req = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val root = JSONObject(resp.body?.string() ?: "{}")
                        val candidate = root.optJSONArray("candidates")?.optJSONObject(0)
                        val summary = candidate?.optJSONObject("content")
                            ?.optJSONArray("parts")
                            ?.optJSONObject(0)
                            ?.optString("text", "") ?: ""

                        val urls = mutableListOf<String>()
                        val chunks = candidate?.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks")
                        if (chunks != null) {
                            for (i in 0 until chunks.length()) {
                                val web = chunks.getJSONObject(i).optJSONObject("web")
                                val uri = web?.optString("uri", "") ?: ""
                                if (uri.isNotEmpty()) urls.add(uri)
                            }
                        }
                        if (summary.isNotBlank()) {
                            return WebGroundingResult(
                                sources = urls.distinct().take(5),
                                contextBlock = "[LIVE GOOGLE SEARCH GROUNDING]\n$summary"
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Fall through to lightweight web fallback
            }
        }

        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val req = Request.Builder()
                .url("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: "{}"
                val json = JSONObject(body)
                val abstractText = json.optString("AbstractText", "")
                val abstractUrl = json.optString("AbstractURL", "")
                if (abstractText.isNotBlank()) {
                    WebGroundingResult(
                        sources = if (abstractUrl.isNotBlank()) listOf(abstractUrl) else emptyList(),
                        contextBlock = "[LIVE WEB GROUNDING]\n$abstractText"
                    )
                } else {
                    WebGroundingResult(emptyList(), "")
                }
            }
        } catch (_: Exception) {
            WebGroundingResult(emptyList(), "")
        }
    }
}
