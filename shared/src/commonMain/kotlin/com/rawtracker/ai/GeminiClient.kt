package com.rawtracker.ai

import com.rawtracker.data.ParsedFood
import com.rawtracker.data.ParsedFoodItem
import com.rawtracker.i18n.strings
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
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private const val PRIMARY_MODEL = "gemini-3.1-flash-lite"
private const val FALLBACK_MODEL = "gemini-2.5-flash"
private val DEFAULT_MODELS = listOf(PRIMARY_MODEL, FALLBACK_MODEL)
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
        "2. INVENTORY VS CONSUMPTION: If you see full tubs, packages, wrappers, bottles, bags, " +
        "or nutrition labels, DO NOT calculate the whole container. Estimate only the extracted " +
        "portion physically plated for immediate consumption. If exact volume is obscured, default " +
        "to a standard single-human serving (e.g. 30g spread, 100g yogurt/quark serving, 50g sliced " +
        "deli meat) unless the user's text explicitly says they ate the full package.\n" +
        "3. PORTION ANCHORING: For each item assign a realistic culinary portion in grams using " +
        "visual scale cues (e.g. a small breakfast sausage ~30g vs a large dinner sausage ~100g; " +
        "assume a 2-3 egg scramble unless the volume is clearly huge). Then estimate that single " +
        "item's calories and macros for that one portion as shown.\n" +
        "4. RAW VS COOKED: For meat, fish, rice, pasta, potatoes, and vegetables, use cooked/ready-" +
        "to-eat nutrition and cooked weights unless the user explicitly says raw, uncooked, dry, " +
        "or gives a raw package weight. A plain cooked chicken breast with no grams defaults to " +
        "~150g cooked, not a whole raw grocery pack.\n" +
        "5. SIZE WORDS MATTER: Treat words like tiny, small, kids, half, light, regular, large, " +
        "double, extra, and family size as hard portion cues. `small fries` means a small side, " +
        "not a generic restaurant fries order.\n" +
        "6. MULTIPLIER LAST: First estimate exactly what is contained within the borders of ONE " +
        "image / single serving. Set `portion_multiplier` to 1 unless the user explicitly states a " +
        "multiplier (e.g. \"two plates\", \"x2\"). Never inflate the per-item baseline to fake a " +
        "multiplier; apply the multiplier only as the final arithmetic step.\n" +
        "7. SANITY CHECK before finalizing:\n" +
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
    private val httpClient: HttpClient = defaultClient(),
    private val modelIds: List<String> = DEFAULT_MODELS
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
        Result.failure(IllegalStateException(explainGeminiFailure(err), err))
    }

    private suspend fun parseOrThrow(text: String?, imageBytes: ByteArray?): ParsedFood {
        val apiKey = apiKeyProvider()
        require(apiKey.isNotBlank()) { "Missing GEMINI_API_KEY (add your key in Settings)" }
        require(!text.isNullOrBlank() || imageBytes != null) { "Provide text or an image" }

        val request = buildRequest(text, imageBytes)
        val rawBody = postWithModelFallback(apiKey, request)
        val envelope = try {
            lenientJson.decodeFromString(GeminiResponse.serializer(), rawBody)
        } catch (err: SerializationException) {
            throw GeminiResponseException(GeminiResponseFailure.ENVELOPE, err)
        }
        val jsonText = envelope.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw GeminiResponseException(GeminiResponseFailure.EMPTY)
        val parsed = try {
            lenientJson.decodeFromString(ParsedFood.serializer(), jsonText)
        } catch (err: SerializationException) {
            throw GeminiResponseException(GeminiResponseFailure.MACROS, err)
        }
        return parsed.withRealityCheck(text = text, hasImage = imageBytes != null)
    }

    private suspend fun postWithModelFallback(apiKey: String, request: GeminiRequest): String {
        var lastModelError: GeminiHttpException? = null
        val candidates = modelIds.distinct().ifEmpty { DEFAULT_MODELS }
        candidates.forEachIndexed { index, modelId ->
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey"
            try {
                return postWithRetry(url, request)
            } catch (err: GeminiHttpException) {
                if (err.isModelUnavailable() && index < candidates.lastIndex) {
                    lastModelError = err
                } else {
                    throw err
                }
            }
        }
        throw lastModelError ?: IllegalStateException("Gemini request failed")
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
                    val status = response.status.value
                    val apiError = runCatching {
                        lenientJson.decodeFromString(GeminiErrorEnvelope.serializer(), rawBody).error
                    }.getOrNull()
                    throw GeminiHttpException(
                        httpStatus = status,
                        apiStatus = apiError?.status,
                        message = apiError?.message?.takeIf { it.isNotBlank() }
                            ?: "Gemini request failed ($status)",
                    )
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

private val RETRYABLE_HTTP_STATUSES = setOf(408, 429, 500, 502, 503, 504)

private enum class GeminiResponseFailure { ENVELOPE, EMPTY, MACROS }

private class GeminiHttpException(
    val httpStatus: Int,
    val apiStatus: String?,
    message: String,
) : IllegalStateException(message)

private class GeminiResponseException(
    val failure: GeminiResponseFailure,
    cause: Throwable? = null,
) : IllegalStateException(cause)

private fun Throwable.isTransientGeminiFailure(): Boolean {
    if (this is GeminiHttpException && httpStatus in RETRYABLE_HTTP_STATUSES) return true
    if (this is HttpRequestTimeoutException || this is SocketTimeoutException) return true
    val text = geminiErrorText()
    return text.contains("timeout", ignoreCase = true) ||
        text.contains("temporarily unavailable", ignoreCase = true) ||
        text.contains("overloaded", ignoreCase = true)
}

private fun GeminiHttpException.isModelUnavailable(): Boolean {
    val text = message.orEmpty()
    return httpStatus == 404 ||
        apiStatus.equals("NOT_FOUND", ignoreCase = true) ||
        text.contains("model", ignoreCase = true) && text.contains("not found", ignoreCase = true) ||
        text.contains("model", ignoreCase = true) && text.contains("not available", ignoreCase = true)
}

private fun ParsedFood.withRealityCheck(text: String?, hasImage: Boolean): ParsedFood {
    val source = listOfNotNull(text, food_name, items.joinToString(" ") { it.name })
        .joinToString(" ")
        .lowercase()
    val warnings = buildList {
        if (protein_g > 75) {
            add("Protein looks unusually high for one meal. Check this did not count a full package or multiple servings.")
        } else if (protein_g > 55 && !source.containsAny("double", "two", "x2", "large", "family")) {
            add("Protein is high for a single serving. Confirm the portion size before saving.")
        }
        if (fat_g > 80) {
            add("Fat looks unusually high for one meal. Check oils, fried foods, or package portions.")
        }
        if (calories > 1500 && !source.containsAny("double", "two", "x2", "large", "family", "whole")) {
            add("Calories look high for a single meal. Confirm this was not estimated as a multi-serving plate.")
        }
        if (hasImage && looksLikeContainerInventory(items, source)) {
            add("This may have counted package inventory. Save only if you ate the full tub/pack shown.")
        }
        if (source.contains("small") && source.containsAny("fries", "chips") && (calories > 380 || fat_g > 22)) {
            add("Small fries usually should not estimate like a large restaurant side. Check the size.")
        }
        if (source.contains("chicken") && !source.contains("raw") && protein_g > 65 && carbs_g < 20) {
            add("Chicken protein is very high. For unspecified grilled chicken, use a cooked single-serving portion.")
        }
    }
    return if (warnings.isEmpty()) this else copy(plausibilityWarnings = warnings.distinct())
}

private fun looksLikeContainerInventory(items: List<ParsedFoodItem>, source: String): Boolean {
    val inventoryWords = listOf(
        "tub", "container", "package", "pack", "wrapper", "bottle", "jar", "carton", "label",
        "quark", "yogurt", "turkey", "ham", "deli"
    )
    val sourceLooksPackaged = source.containsAny(*inventoryWords.toTypedArray())
    val veryLargeItem = items.any { it.portion_grams >= 350 }
    val explicitPackageWeight = Regex("""\b[2-9]\d{2}\s?g\b|\b1\s?kg\b""").containsMatchIn(source)
    return sourceLooksPackaged && (veryLargeItem || explicitPackageWeight)
}

private fun String.containsAny(vararg needles: String): Boolean =
    needles.any { contains(it, ignoreCase = true) }

private fun Throwable.geminiErrorText(): String = generateSequence(this) { it.cause }
    .mapNotNull { it.message?.takeIf { msg -> msg.isNotBlank() } }
    .joinToString(" | ")

private fun explainGeminiFailure(err: Throwable): String {
    val http = err.findGeminiHttpException()
    val response = err.findGeminiResponseException()
    val text = err.geminiErrorText()
    val types = err.exceptionTypeChain()

    if (text.contains("Missing GEMINI_API_KEY", ignoreCase = true)) {
        return strings.missingGeminiKey
    }
    if (text.contains("Provide text or an image", ignoreCase = true)) {
        return strings.provideFoodInput
    }
    // A connectivity failure (no internet / DNS) often has an empty message, so detect it by
    // exception TYPE as well as text — otherwise it falls through to a misleading message.
    if (http == null && isNetworkProblem(text, types)) {
        return strings.cannotReachGemini
    }
    // Account/key problems are only meaningful when Google actually answered with an HTTP error.
    if (http != null) {
        return explainHttpError(http, text)
    }
    if (err.isTransientGeminiFailure()) {
        return strings.geminiTimeout
    }
    when (response?.failure) {
        GeminiResponseFailure.EMPTY ->
            return strings.geminiEmptyEstimate
        GeminiResponseFailure.MACROS ->
            return strings.geminiGarbled
        GeminiResponseFailure.ENVELOPE ->
            return strings.geminiUnexpected
        null -> Unit
    }
    if (err is SerializationException) {
        return strings.geminiGarbled
    }
    return strings.cannotReachGemini
}

private fun explainHttpError(http: GeminiHttpException, text: String): String = when {
    isApiKeyProblem(http, text) ->
        strings.geminiKeyRejected
    isAccountAccessProblem(http, text) ->
        strings.geminiKeyDenied
    http.httpStatus == 429 || isRateLimitProblem(text) ->
        strings.geminiRateLimit
    http.httpStatus == 404 || text.contains("is not found", ignoreCase = true) ->
        strings.geminiModelUnavailable
    http.httpStatus in setOf(503, 502, 504) || text.contains("overloaded", ignoreCase = true) ->
        strings.geminiBusy
    http.httpStatus in 500..599 ->
        strings.geminiServerGlitch
    http.httpStatus in 400..499 ->
        strings.geminiRejected(http.httpStatus)
    else ->
        strings.geminiReturnedError(http.httpStatus)
}

private fun Throwable.findGeminiHttpException(): GeminiHttpException? =
    generateSequence(this) { it.cause }.filterIsInstance<GeminiHttpException>().firstOrNull()

private fun Throwable.findGeminiResponseException(): GeminiResponseException? =
    generateSequence(this) { it.cause }.filterIsInstance<GeminiResponseException>().firstOrNull()

private fun Throwable.exceptionTypeChain(): String =
    generateSequence(this) { it.cause }.joinToString(" | ") { it::class.simpleName.orEmpty() }

/** 401/invalid-credential and "API key not valid" → the key itself is rejected. */
private fun isApiKeyProblem(http: GeminiHttpException, text: String): Boolean =
    http.httpStatus == 401 ||
        http.apiStatus.equals("UNAUTHENTICATED", ignoreCase = true) ||
        text.contains("API key not valid", ignoreCase = true) ||
        text.contains("API_KEY_INVALID", ignoreCase = true) ||
        text.contains("invalid authentication credentials", ignoreCase = true) ||
        text.contains("ACCESS_TOKEN_TYPE_UNSUPPORTED", ignoreCase = true) ||
        (http.apiStatus.equals("INVALID_ARGUMENT", ignoreCase = true) && text.contains("api key", ignoreCase = true))

/** 403/permission/billing → key is valid-looking but the account can't use it. */
private fun isAccountAccessProblem(http: GeminiHttpException, text: String): Boolean =
    http.httpStatus == 403 ||
        http.apiStatus.equals("PERMISSION_DENIED", ignoreCase = true) ||
        text.contains("PERMISSION_DENIED", ignoreCase = true) ||
        text.contains("billing", ignoreCase = true)

private fun isRateLimitProblem(text: String): Boolean =
    text.contains("resource exhausted", ignoreCase = true) ||
        text.contains("rate limit", ignoreCase = true) ||
        text.contains("quota", ignoreCase = true) ||
        text.contains("too many requests", ignoreCase = true)

private val NETWORK_EXCEPTION_HINTS = listOf(
    "UnresolvedAddressException",
    "UnknownHostException",
    "ConnectException",
    "NoRouteToHostException",
    "PortUnreachableException",
    "SocketException",
    "ConnectTimeoutException",
)

private fun isNetworkProblem(text: String, types: String): Boolean =
    NETWORK_EXCEPTION_HINTS.any { types.contains(it, ignoreCase = true) } ||
        text.contains("Unable to resolve host", ignoreCase = true) ||
        text.contains("Network is unreachable", ignoreCase = true) ||
        text.contains("Connection reset", ignoreCase = true) ||
        text.contains("Connection refused", ignoreCase = true) ||
        text.contains("No address associated with hostname", ignoreCase = true) ||
        text.contains("Software caused connection abort", ignoreCase = true) ||
        text.contains("Failed to connect", ignoreCase = true)

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
