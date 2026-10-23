package com.bro.assistant.ai

import android.content.Context
import com.bro.assistant.security.SecretBox
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

enum class BroSkill {
    CODE, FAST, LONG, REASON, GENERAL
}

enum class ProviderId(val displayName: String, val keyHint: String) {
    CLAUDE("Claude (Native)", "sk-ant-..."),
    OPENROUTER_CLAUDE("Claude via OpenRouter", "Uses OpenRouter Key"),
    GEMINI("Google Gemini", "AIzaSy..."),
    GROQ("Groq LPU", "gsk_..."),
    CEREBRAS("Cerebras", "csk-..."),
    MISTRAL("Mistral AI", "Mistral API Key"),
    OPENROUTER("OpenRouter (Free/Auto)", "sk-or-v1-..."),
    NVIDIA("NVIDIA NIM", "nvapi-..."),
    GITHUB("GitHub Models", "ghp_... or github_pat_..."),
    CLOUDFLARE("Cloudflare Workers AI", "ACCOUNT_ID:API_TOKEN"),
    COLAB_QWEN("Colab Qwen Tunnel", "URL SECRET")
}

data class AiStreamResult(
    val providerUsed: ProviderId,
    val fullText: String,
    val groundingSources: List<String> = emptyList()
)

object Providers {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val cooldownUntil = ConcurrentHashMap<ProviderId, Long>()
    private const val COOLDOWN_MS = 60_000L
    private val activeCall = AtomicReference<Call?>(null)

    fun cancelActiveJob() {
        activeCall.getAndSet(null)?.cancel()
    }

    fun isCoolingDown(provider: ProviderId): Boolean {
        val until = cooldownUntil[provider] ?: return false
        if (System.currentTimeMillis() < until) return true
        cooldownUntil.remove(provider)
        return false
    }

    private fun markCooldown(provider: ProviderId) {
        cooldownUntil[provider] = System.currentTimeMillis() + COOLDOWN_MS
    }

    fun getSkillOrder(skill: BroSkill): List<ProviderId> {
        return when (skill) {
            BroSkill.FAST -> listOf(
                ProviderId.GROQ, ProviderId.CEREBRAS, ProviderId.GEMINI,
                ProviderId.MISTRAL, ProviderId.OPENROUTER, ProviderId.CLOUDFLARE,
                ProviderId.GITHUB, ProviderId.NVIDIA, ProviderId.COLAB_QWEN,
                ProviderId.OPENROUTER_CLAUDE, ProviderId.CLAUDE
            )
            BroSkill.CODE -> listOf(
                ProviderId.CLAUDE, ProviderId.OPENROUTER_CLAUDE, ProviderId.COLAB_QWEN,
                ProviderId.GEMINI, ProviderId.GITHUB, ProviderId.MISTRAL,
                ProviderId.OPENROUTER, ProviderId.GROQ, ProviderId.CEREBRAS,
                ProviderId.NVIDIA, ProviderId.CLOUDFLARE
            )
            BroSkill.REASON -> listOf(
                ProviderId.CLAUDE, ProviderId.OPENROUTER_CLAUDE, ProviderId.GEMINI,
                ProviderId.GITHUB, ProviderId.NVIDIA, ProviderId.OPENROUTER,
                ProviderId.GROQ, ProviderId.CEREBRAS, ProviderId.MISTRAL,
                ProviderId.COLAB_QWEN, ProviderId.CLOUDFLARE
            )
            BroSkill.LONG -> listOf(
                ProviderId.CLAUDE, ProviderId.OPENROUTER_CLAUDE, ProviderId.GEMINI,
                ProviderId.OPENROUTER, ProviderId.MISTRAL, ProviderId.GITHUB,
                ProviderId.GROQ, ProviderId.NVIDIA, ProviderId.CEREBRAS,
                ProviderId.COLAB_QWEN, ProviderId.CLOUDFLARE
            )
            BroSkill.GENERAL -> listOf(
                ProviderId.CLAUDE, ProviderId.OPENROUTER_CLAUDE, ProviderId.GEMINI,
                ProviderId.GROQ, ProviderId.CEREBRAS, ProviderId.MISTRAL,
                ProviderId.OPENROUTER, ProviderId.GITHUB, ProviderId.NVIDIA,
                ProviderId.CLOUDFLARE, ProviderId.COLAB_QWEN
            )
        }.distinct()
    }

    private fun resolveKey(context: Context, provider: ProviderId): String {
        return when (provider) {
            ProviderId.OPENROUTER_CLAUDE -> SecretBox.getKey(context, ProviderId.OPENROUTER.name)
            else -> SecretBox.getKey(context, provider.name)
        }
    }

    suspend fun streamChat(
        context: Context,
        prompt: String,
        systemPrompt: String = "You are BRO, a fast, tactical AI assistant.",
        skill: BroSkill = BroSkill.GENERAL,
        enableWebGrounding: Boolean = false,
        onStatus: (String) -> Unit = {},
        onToken: (String) -> Unit
    ): Result<AiStreamResult> = withContext(Dispatchers.IO) {
        val candidates = getSkillOrder(skill)
        val errors = mutableListOf<String>()

        val groundingContext = if (enableWebGrounding) {
            onStatus("GROUNDING WEB...")
            WebGrounding.fetchSearchSnippets(context, prompt, httpClient)
        } else {
            WebGroundingResult(emptyList(), "")
        }

        val augmentedPrompt = if (groundingContext.contextBlock.isNotBlank()) {
            "${groundingContext.contextBlock}\n\nUser Question: $prompt"
        } else {
            prompt
        }

        for (provider in candidates) {
            currentCoroutineContext().ensureActive()
            if (isCoolingDown(provider)) continue

            val rawKey = resolveKey(context, provider)
            if (rawKey.isBlank()) continue

            onStatus("ROUTING -> ${provider.displayName}")
            try {
                val out = StringBuilder()
                when (provider) {
                    ProviderId.CLAUDE -> streamAnthropic(rawKey, systemPrompt, augmentedPrompt) {
                        out.append(it); onToken(it)
                    }
                    ProviderId.GEMINI -> streamGemini(rawKey, systemPrompt, prompt, enableWebGrounding) {
                        out.append(it); onToken(it)
                    }
                    ProviderId.CLOUDFLARE -> callCloudflare(rawKey, systemPrompt, augmentedPrompt) {
                        out.append(it); onToken(it)
                    }
                    ProviderId.COLAB_QWEN -> callColabQwen(rawKey, systemPrompt, augmentedPrompt) {
                        out.append(it); onToken(it)
                    }
                    else -> streamOpenAiCompatible(provider, rawKey, systemPrompt, augmentedPrompt) {
                        out.append(it); onToken(it)
                    }
                }

                if (out.isNotEmpty()) {
                    return@withContext Result.success(
                        AiStreamResult(
                            providerUsed = provider,
                            fullText = out.toString(),
                            groundingSources = groundingContext.sources
                        )
                    )
                }
            } catch (ce: CancellationException) {
                cancelActiveJob()
                throw ce
            } catch (e: HttpStatusException) {
                if (e.code == 429) {
                    markCooldown(provider)
                    errors.add("${provider.displayName}: 429 Rate Limit")
                } else {
                    errors.add("${provider.displayName}: HTTP ${e.code}")
                }
            } catch (e: Exception) {
                if (e.message?.contains("Canceled", ignoreCase = true) == true) {
                    throw CancellationException("Job cancelled")
                }
                errors.add("${provider.displayName}: ${e.localizedMessage ?: "Failed"}")
            }
        }

        Result.failure(IllegalStateException("All providers failed or no keys configured.\n" + errors.joinToString("\n")))
    }

    private fun streamAnthropic(apiKey: String, system: String, prompt: String, onToken: (String) -> Unit) {
        val payload = JSONObject().apply {
            put("model", "claude-3-5-sonnet-latest")
            put("max_tokens", 4096)
            put("stream", true)
            put("system", system)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        }
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        executeSseStream(request) { line ->
            if (line != "[DONE]") {
                val json = JSONObject(line)
                if (json.optString("type") == "content_block_delta") {
                    val text = json.optJSONObject("delta")?.optString("text", "") ?: ""
                    if (text.isNotEmpty()) onToken(text)
                }
            }
        }
    }

    private fun streamGemini(apiKey: String, system: String, prompt: String, search: Boolean, onToken: (String) -> Unit) {
        val payload = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            if (search) put("tools", JSONArray().put(JSONObject().put("googleSearch", JSONObject())))
        }
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse&key=$apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        executeSseStream(request) { line ->
            if (line != "[DONE]") {
                val json = JSONObject(line)
                val cands = json.optJSONArray("candidates") ?: return@executeSseStream
                if (cands.length() > 0) {
                    val parts = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts") ?: return@executeSseStream
                    for (i in 0 until parts.length()) {
                        val t = parts.getJSONObject(i).optString("text", "")
                        if (t.isNotEmpty()) onToken(t)
                    }
                }
            }
        }
    }

    private fun streamOpenAiCompatible(provider: ProviderId, apiKey: String, system: String, prompt: String, onToken: (String) -> Unit) {
        val (endpoint, model) = when (provider) {
            ProviderId.GROQ -> "https://api.groq.com/openai/v1/chat/completions" to "llama-3.3-70b-versatile"
            ProviderId.CEREBRAS -> "https://api.cerebras.ai/v1/chat/completions" to "llama-3.3-70b"
            ProviderId.MISTRAL -> "https://api.mistral.ai/v1/chat/completions" to "codestral-latest"
            ProviderId.NVIDIA -> "https://integrate.api.nvidia.com/v1/chat/completions" to "meta/llama-3.1-70b-instruct"
            ProviderId.GITHUB -> "https://models.inference.ai.azure.com/chat/completions" to "gpt-4o"
            ProviderId.OPENROUTER_CLAUDE -> "https://openrouter.ai/api/v1/chat/completions" to "anthropic/claude-3.5-sonnet"
            else -> "https://openrouter.ai/api/v1/chat/completions" to "deepseek/deepseek-chat:free"
        }

        val payload = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", prompt))
            )
            put("stream", true)
        }

        val reqBuilder = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")

        val request = reqBuilder.post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        executeSseStream(request) { line ->
            if (line != "[DONE]") {
                val json = JSONObject(line)
                val choices = json.optJSONArray("choices") ?: return@executeSseStream
                if (choices.length() > 0) {
                    val content = choices.getJSONObject(0).optJSONObject("delta")?.optString("content", "") ?: ""
                    if (content.isNotEmpty()) onToken(content)
                }
            }
        }
    }

    private fun callCloudflare(key: String, system: String, prompt: String, onToken: (String) -> Unit) {
        val parts = key.split(":", limit = 2)
        val payload = JSONObject().apply {
            put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", prompt))
            )
        }
        val request = Request.Builder()
            .url("https://api.cloudflare.com/client/v4/accounts/${parts[0].trim()}/ai/run/@cf/meta/llama-3.1-8b-instruct")
            .addHeader("Authorization", "Bearer ${parts[1].trim()}")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val call = httpClient.newCall(request)
        activeCall.set(call)
        call.execute().use { resp ->
            if (!resp.isSuccessful) throw HttpStatusException(resp.code)
            val text = JSONObject(resp.body?.string() ?: "{}").optJSONObject("result")?.optString("response", "") ?: ""
            if (text.isNotEmpty()) onToken(text)
        }
    }

    private fun callColabQwen(key: String, system: String, prompt: String, onToken: (String) -> Unit) {
        val parts = key.trim().split("\\s+|\\|".toRegex(), limit = 2)
        val payload = JSONObject().apply {
            put("model", "qwen2.5-coder")
            put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", prompt))
            )
        }
        val reqBuilder = Request.Builder()
            .url("${parts[0].trim().removeSuffix("/")}/v1/chat/completions")
            .addHeader("Content-Type", "application/json")
        if (parts.size > 1) reqBuilder.addHeader("Authorization", "Bearer ${parts[1].trim()}")

        val call = httpClient.newCall(reqBuilder.post(payload.toString().toRequestBody("application/json".toMediaType())).build())
        activeCall.set(call)
        call.execute().use { resp ->
            if (!resp.isSuccessful) throw HttpStatusException(resp.code)
            val root = JSONObject(resp.body?.string() ?: "{}")
            val text = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content", "") ?: ""
            if (text.isNotEmpty()) onToken(text)
        }
    }

    private fun executeSseStream(request: Request, onLine: (String) -> Unit) {
        val call = httpClient.newCall(request)
        activeCall.set(call)
        call.execute().use { resp ->
            if (!resp.isSuccessful) throw HttpStatusException(resp.code)
            val stream = resp.body?.byteStream() ?: return
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line?.trim() ?: continue
                    if (trimmed.startsWith("data:")) {
                        val p = trimmed.removePrefix("data:").trim()
                        if (p.isNotEmpty()) onLine(p)
                    }
                }
            }
        }
    }

    class HttpStatusException(val code: Int) : Exception("HTTP $code")
}
