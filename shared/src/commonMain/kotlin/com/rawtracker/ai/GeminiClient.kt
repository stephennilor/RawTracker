package com.rawtracker.ai

import com.rawtracker.data.ParsedFood
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private const val MODEL = "gemini-2.5-flash"
private const val GEMINI_MAX_ATTEMPTS = 2
private const val GEMINI_RETRY_DELAY_MS = 900L
private const val GEMINI_REQUEST_TIMEOUT_MS = 120_000L
private const val GEMINI_CONNECT_TIMEOUT_MS = 15_000L
private const val GEMINI_SOCKET_TIMEOUT_MS = 120_000L

// Smaller models fail at zero-shot volumetric math, so we force a strict component-by-component
// protocol and bake the reasoning INTO the schema (items + multiplier are produced before totals).
private const val SYSTEM_PROMPT =
    "You are a meticulous nutrition estimator. Follow this protocol exactly, in order:\n" +
        "1. COMPONENT ISOLATION: List every distinct food item you can identify in `items`. " +
        "Ignore drinks and background objects unless the user's text explicitly names them.\n" +
        "2. PORTION ANCHORING: For each item assign a realistic culinary portion in grams using " +
        "visual scale cues (e.g. a small breakfast sausage ~30g vs a large dinner sausage ~100g; " +
        "assume a 2-3 egg scramble unless the volume is clearly huge). Then estimate that single " +
        "item's calories and macros for that one portion as shown.\n" +
        "3. MULTIPLIER LAST: First estimate exactly what is contained within the borders of ONE " +
        "image / single serving. Set `portion_multiplier` to 1 unless the user explicitly states a " +
        "multiplier (e.g. \"two plates\", \"x2\"). Never inflate the per-item baseline to fake a " +
        "multiplier; apply the multiplier only as the final arithmetic step.\n" +
        "4. SANITY CHECK before finalizing:\n" +
        "   - PROTEIN: ~100g of protein requires ~450g of dense meat or ~16 large eggs. If a single " +
        "serving exceeds ~50g protein, verify the image plausibly holds that much meat; if not, " +
        "reduce it.\n" +
        "   - CARBS: >80g requires obvious dense starch or sugar (large bread/pasta/rice piles, " +
        "sugary drinks). Otherwise reduce.\n" +
        "   - FAT: account for regional cooking oils (tagines, shakshuka, marinades), but cap " +
        "invisible cooking fat at ~15-20g per mixed dish unless clearly deep-fried.\n" +
        "Respond ONLY with JSON matching the schema, using whole integers for all macro fields. " +
        "The top-level calories/protein_g/carbs_g/fat_g MUST equal the sum across `items` multiplied " +
        "by `portion_multiplier`. `food_name` is a short human label for the whole meal."

class GeminiClient(
    private val apiKeyProvider: suspend () -> String,
    private val httpClient: HttpClient = defaultClient()
) {
    /** Convenience constructor for a fixed key (used by tests and simple wiring). */
    constructor(apiKey: String, httpClient: HttpClient = defaultClient()) :
        this({ apiKey }, httpClient)

    /**
     * Parses food from optional text and/or image bytes into structured macros.
     * Returns a failure (without throwing) on any network or parsing error.
     */
    suspend fun parse(text: String?, imageBytes: ByteArray?): Result<ParsedFood> = try {
        Result.success(parseOrThrow(text, imageBytes))
    } catch (err: CancellationException) {
        throw err
    } catch (err: Throwable) {
        Result.failure(IllegalStateException(err.toUserMessage(), err))
    }

    private suspend fun parseOrThrow(text: String?, imageBytes: ByteArray?): ParsedFood {
        val apiKey = apiKeyProvider()
        require(apiKey.isNotBlank()) { "Missing GEMINI_API_KEY (add your key in Settings)" }
        require(!text.isNullOrBlank() || imageBytes != null) { "Provide text or an image" }

        val request = buildRequest(text, imageBytes)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey"
        val rawBody = postWithRetry(url, request)
        val envelope = lenientJson.decodeFromString(GeminiResponse.serializer(), rawBody)
        val jsonText = envelope.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: error("Gemini returned no result. Try rephrasing.")
        return lenientJson.decodeFromString(ParsedFood.serializer(), jsonText)
    }

    private suspend fun postWithRetry(url: String, request: GeminiRequest): String {
        var lastError: Throwable? = null
        repeat(GEMINI_MAX_ATTEMPTS) { attempt ->
            try {
                val response = httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
                val rawBody = response.bodyAsText()
                if (!response.status.isSuccess()) {
                    val apiMessage = runCatching {
                        lenientJson.decodeFromString(GeminiErrorEnvelope.serializer(), rawBody).error?.message
                    }.getOrNull()
                    error(apiMessage?.takeIf { it.isNotBlank() } ?: "Gemini request failed (${response.status.value})")
                }
                return rawBody
            } catch (err: CancellationException) {
                throw err
            } catch (err: Throwable) {
                lastError = err
                val finalAttempt = attempt == GEMINI_MAX_ATTEMPTS - 1
                if (!err.isTransientGeminiFailure() || finalAttempt) throw err
                delay(GEMINI_RETRY_DELAY_MS)
            }
        }
        throw lastError ?: IllegalStateException("Gemini request failed")
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun buildRequest(text: String?, imageBytes: ByteArray?): GeminiRequest {
        val parts = buildList {
            if (!text.isNullOrBlank()) add(Part(text = text))
            if (imageBytes != null) {
                add(Part(inlineData = InlineData("image/jpeg", Base64.encode(imageBytes))))
            }
            if (isEmpty()) add(Part(text = "Estimate the nutrition."))
        }
        return GeminiRequest(
            contents = listOf(Content(parts = parts)),
            systemInstruction = Content(parts = listOf(Part(text = SYSTEM_PROMPT))),
            generationConfig = macroSchemaConfig()
        )
    }

    private fun macroSchemaConfig(): JsonObject = buildJsonObject {
        put("response_mime_type", "application/json")
        put("temperature", 0.2)
        putJsonObject("response_schema") {
            put("type", "OBJECT")
            putJsonObject("properties") {
                // `items` and `portion_multiplier` come first in propertyOrdering so the model is
                // forced to isolate and size each component (and decide the multiplier) BEFORE it
                // commits to the totals — Chain-of-Thought encoded directly in the output schema.
                putJsonObject("items") {
                    put("type", "ARRAY")
                    putJsonObject("items") {
                        put("type", "OBJECT")
                        putJsonObject("properties") {
                            putJsonObject("name") { put("type", "STRING") }
                            putJsonObject("portion_grams") { put("type", "INTEGER") }
                            putJsonObject("calories") { put("type", "INTEGER") }
                            putJsonObject("protein_g") { put("type", "INTEGER") }
                            putJsonObject("carbs_g") { put("type", "INTEGER") }
                            putJsonObject("fat_g") { put("type", "INTEGER") }
                        }
                        putJsonArray("required") {
                            add("name"); add("portion_grams")
                            add("calories"); add("protein_g"); add("carbs_g"); add("fat_g")
                        }
                        putJsonArray("propertyOrdering") {
                            add("name"); add("portion_grams")
                            add("calories"); add("protein_g"); add("carbs_g"); add("fat_g")
                        }
                    }
                }
                putJsonObject("portion_multiplier") { put("type", "NUMBER") }
                putJsonObject("food_name") { put("type", "STRING") }
                putJsonObject("calories") { put("type", "INTEGER") }
                putJsonObject("protein_g") { put("type", "INTEGER") }
                putJsonObject("carbs_g") { put("type", "INTEGER") }
                putJsonObject("fat_g") { put("type", "INTEGER") }
            }
            putJsonArray("required") {
                add("items"); add("portion_multiplier")
                add("food_name"); add("calories"); add("protein_g"); add("carbs_g"); add("fat_g")
            }
            putJsonArray("propertyOrdering") {
                add("items"); add("portion_multiplier")
                add("food_name"); add("calories"); add("protein_g"); add("carbs_g"); add("fat_g")
            }
        }
    }

    companion object {
        private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }
        fun defaultClient(): HttpClient = HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = GEMINI_REQUEST_TIMEOUT_MS
                connectTimeoutMillis = GEMINI_CONNECT_TIMEOUT_MS
                socketTimeoutMillis = GEMINI_SOCKET_TIMEOUT_MS
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = false })
            }
        }
    }
}

private fun Throwable.isTransientGeminiFailure(): Boolean =
    this is HttpRequestTimeoutException ||
        this is SocketTimeoutException ||
        message.orEmpty().contains("timeout", ignoreCase = true)

private fun Throwable.toUserMessage(): String {
    val raw = message.orEmpty()
    return when {
        raw.contains("Missing GEMINI_API_KEY") -> raw
        raw.contains("Provide text or an image") -> raw
        raw.contains("API key not valid", ignoreCase = true) ->
            "Gemini API key is not valid. Check Settings."
        isTransientGeminiFailure() ->
            "Gemini took too long to respond. Try again, or use a shorter prompt/photo."
        raw.contains("Gemini returned no result", ignoreCase = true) ->
            "Gemini returned no result. Try rephrasing."
        raw.contains("Gemini request failed", ignoreCase = true) ->
            raw.take(120)
        else -> "Gemini could not parse this. Try again in a moment."
    }
}

@Serializable
private data class GeminiRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null,
    val generationConfig: JsonObject
)

@Serializable
private data class Content(val parts: List<Part>, val role: String? = null)

@Serializable
private data class Part(
    val text: String? = null,
    @SerialName("inline_data") val inlineData: InlineData? = null
)

@Serializable
private data class InlineData(
    @SerialName("mime_type") val mimeType: String,
    val data: String
)

@Serializable
private data class GeminiResponse(val candidates: List<Candidate> = emptyList())

@Serializable
private data class Candidate(val content: Content? = null)

@Serializable
private data class GeminiErrorEnvelope(val error: GeminiError? = null)

@Serializable
private data class GeminiError(val code: Int? = null, val message: String? = null, val status: String? = null)
