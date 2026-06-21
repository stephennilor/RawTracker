package com.rawtracker.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val GEMINI_ENVELOPE = """
{
  "candidates": [
    {
      "content": {
        "parts": [
          { "text": "{\"food_name\":\"Chicken Burrito Bowl\",\"calories\":640,\"protein_g\":46,\"carbs_g\":58,\"fat_g\":22}" }
        ]
      }
    }
  ]
}
"""

private fun mockClient(
    status: HttpStatusCode = HttpStatusCode.OK,
    body: String = GEMINI_ENVELOPE,
    onRequest: (HttpRequestData) -> Unit = {}
): HttpClient {
    val engine = MockEngine { request ->
        onRequest(request)
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }
    return HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = false })
        }
    }
}

class GeminiClientTest {

    @Test
    fun parsesStructuredMacrosFromTextResponse() = runTest {
        val client = GeminiClient(apiKey = "test-key", httpClient = mockClient())

        val result = client.parse(text = "chicken burrito bowl", imageBytes = null)

        assertTrue(result.isSuccess, "expected success, got ${result.exceptionOrNull()}")
        val food = result.getOrThrow()
        assertEquals("Chicken Burrito Bowl", food.food_name)
        assertEquals(640, food.calories)
        assertEquals(46, food.protein_g)
        assertEquals(58, food.carbs_g)
        assertEquals(22, food.fat_g)
    }

    @Test
    fun failsWhenApiKeyBlank() = runTest {
        val client = GeminiClient(apiKey = "", httpClient = mockClient())

        val result = client.parse(text = "a banana", imageBytes = null)

        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()?.message ?: "", "GEMINI_API_KEY")
    }

    @Test
    fun failsWhenNeitherTextNorImageProvided() = runTest {
        val client = GeminiClient(apiKey = "test-key", httpClient = mockClient())

        val result = client.parse(text = null, imageBytes = null)

        assertTrue(result.isFailure)
    }

    @Test
    fun surfacesCleanMessageOnApiError() = runTest {
        val errorBody = """
            {"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT"}}
        """
        val client = GeminiClient(
            apiKey = "bad-key",
            httpClient = mockClient(status = HttpStatusCode.BadRequest, body = errorBody)
        )

        val result = client.parse(text = "a sandwich", imageBytes = null)

        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()?.message ?: "", "API key is not valid")
    }

    @Test
    fun failsGracefullyWhenCandidatesEmpty() = runTest {
        val client = GeminiClient(
            apiKey = "test-key",
            httpClient = mockClient(body = """{ "candidates": [] }""")
        )

        val result = client.parse(text = "mystery food", imageBytes = null)

        assertTrue(result.isFailure)
    }

    @Test
    fun sanitizesTransportErrorsBeforeSurfacingToUi() = runTest {
        val engine = MockEngine {
            error(
                "Request timeout has expired [url=https://generativelanguage.googleapis.com/" +
                    "v1beta/models/gemini-2.5-flash:generateContent?key=secret-key, request_timeout=unknown ms]"
            )
        }
        val client = GeminiClient(
            apiKey = "secret-key",
            httpClient = HttpClient(engine) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; encodeDefaults = false })
                }
            }
        )

        val result = client.parse(text = "a sandwich", imageBytes = null)
        val message = result.exceptionOrNull()?.message.orEmpty()

        assertTrue(result.isFailure)
        assertEquals("Gemini took too long to respond. Try again, or use a shorter prompt/photo.", message)
        assertTrue(!message.contains("secret-key"))
        assertTrue(!message.contains("generativelanguage.googleapis.com"))
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun sendsImageBytesAsBase64InlineData() = runTest {
        val imageBytes = byteArrayOf(1, 2, 3, 4, 5)
        val expectedB64 = Base64.encode(imageBytes)
        var capturedBody = ""

        val engine = MockEngine { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = GEMINI_ENVELOPE,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val capturingClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = false })
            }
        }

        val result = GeminiClient("test-key", capturingClient)
            .parse(text = "with photo", imageBytes = imageBytes)

        assertTrue(result.isSuccess, "expected success, got ${result.exceptionOrNull()}")
        assertContains(capturedBody, expectedB64)
        assertContains(capturedBody, "inline_data")
        assertContains(capturedBody, "with photo")
    }
}
