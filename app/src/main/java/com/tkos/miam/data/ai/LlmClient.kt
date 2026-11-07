package com.tkos.miam.data.ai

import com.tkos.miam.data.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

/** Fournisseurs pris en charge. L'utilisateur utilise son propre compte : rien ne passe par un serveur Miam. */
enum class AiProvider(val label: String, val defaultModel: String, val defaultBaseUrl: String, val needsKey: Boolean) {
    NONE("Désactivé", "", "", false),
    ANTHROPIC("Anthropic (Claude)", "claude-haiku-5-5", "https://api.anthropic.com", true),
    OPENAI("OpenAI (ChatGPT)", "gpt-4o-mini", "https://api.openai.com", true),
    MISTRAL("Mistral", "mistral-small-latest", "https://api.mistral.ai", true),
    OLLAMA("Ollama / serveur local", "llama3.1", "http://192.168.1.10:11434", false),
    OPENAI_COMPATIBLE("Autre (API compatible OpenAI)", "", "", true);

    companion object {
        fun fromName(name: String?): AiProvider = entries.firstOrNull { it.name == name } ?: NONE
    }
}

/** Client minimal (HttpURLConnection) : un appel = un message système + un message utilisateur → texte. */
class LlmClient(private val provider: AiProvider, private val apiKey: String, model: String, baseUrl: String) {
    private val model = model.ifBlank { provider.defaultModel }
    private val baseUrl = baseUrl.ifBlank { provider.defaultBaseUrl }.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true }

    val isConfigured: Boolean
        get() = provider != AiProvider.NONE && (!provider.needsKey || apiKey.isNotBlank()) && baseUrl.isNotBlank() && model.isNotBlank()

    companion object {
        fun from(s: Settings): LlmClient = LlmClient(AiProvider.fromName(s.aiProvider), s.aiApiKey, s.aiModel, s.aiBaseUrl)

        /** Extrait le premier objet ou tableau JSON d'une réponse (les modèles aiment entourer de ```json). */
        fun extractJson(text: String): String {
            val t = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val starts = listOf(t.indexOf('{'), t.indexOf('[')).filter { it >= 0 }
            if (starts.isEmpty()) return t
            val start = starts.min()
            val open = t[start]
            val close = if (open == '{') '}' else ']'
            var depth = 0
            var inStr = false
            var esc = false
            for (i in start until t.length) {
                val c = t[i]
                if (inStr) {
                    if (esc) esc = false else if (c == '\\') esc = true else if (c == '"') inStr = false
                } else when (c) {
                    '"' -> inStr = true
                    open -> depth++
                    close -> { depth--; if (depth == 0) return t.substring(start, i + 1) }
                }
            }
            return t.substring(start)
        }
    }

    suspend fun complete(system: String, user: String, maxTokens: Int = 4000): String = withContext(Dispatchers.IO) {
        require(isConfigured) { "Assistant IA non configuré (Paramètres → Assistant IA)" }
        when (provider) {
            AiProvider.ANTHROPIC -> anthropic(system, user, maxTokens)
            AiProvider.OLLAMA -> ollama(system, user)
            else -> openAiCompatible(system, user, maxTokens)
        }
    }

    private fun anthropic(system: String, user: String, maxTokens: Int): String {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            put("system", system)
            put("messages", buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", user) }) })
        }
        val res = post("$baseUrl/v1/messages", body.toString(), mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01"))
        val content = res.jsonObject["content"]?.jsonArray ?: error("Réponse inattendue")
        return content.filter { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
            .joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content ?: "" }
    }

    private fun openAiCompatible(system: String, user: String, maxTokens: Int): String {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            put("temperature", 0.3)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject { put("role", "user"); put("content", user) })
            })
        }
        val res = post("$baseUrl/v1/chat/completions", body.toString(), mapOf("Authorization" to "Bearer $apiKey"))
        return res.jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?: error("Réponse inattendue")
    }

    private fun ollama(system: String, user: String): String {
        val body = buildJsonObject {
            put("model", model)
            put("stream", false)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject { put("role", "user"); put("content", user) })
            })
        }
        val headers = if (apiKey.isNotBlank()) mapOf("Authorization" to "Bearer $apiKey") else emptyMap()
        val res = post("$baseUrl/api/chat", body.toString(), headers)
        return res.jsonObject["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content ?: error("Réponse inattendue")
    }

    private fun post(url: String, body: String, headers: Map<String, String>): kotlinx.serialization.json.JsonElement {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Miam-Android")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = runCatching { json.parseToJsonElement(text).jsonObject["error"]?.let { e -> (e as? JsonObject)?.get("message")?.jsonPrimitive?.content ?: (e as? JsonPrimitive)?.content } }.getOrNull()
                error("HTTP $code${if (!msg.isNullOrBlank()) " : $msg" else ""}")
            }
            return json.parseToJsonElement(text)
        } finally {
            conn.disconnect()
        }
    }
}

@Suppress("unused")
private fun JsonArray.firstObject(): JsonObject? = firstOrNull()?.jsonObject
