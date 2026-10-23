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

data class WebGroundingResult(val sources: List<String>, val contextBlock: String)

object WebGrounding {
    fun fetchSearchSnippets(context: Context, query: String, client: OkHttpClient): WebGroundingResult {
        val key = SecretBox.getKey(context, ProviderId.GEMINI.name)
        if (key.isNotBlank()) {
            try {
                val payload = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", "Web search notes for: $query")))))
                    put("tools", JSONArray().put(JSONObject().put("googleSearch", JSONObject())))
                }
                val req = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$key")
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val root = JSONObject(resp.body?.string() ?: "{}")
                        val cand = root.optJSONArray("candidates")?.optJSONObject(0)
                        val summary = cand?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text", "") ?: ""
                        if (summary.isNotBlank()) return WebGroundingResult(emptyList(), "[WEB SEARCH]\n$summary")
                    }
                }
            } catch (_: Exception) {}
        }
        return WebGroundingResult(emptyList(), "")
    }
}
