package com.rawtracker

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.rawtracker.ai.GeminiClient
import com.rawtracker.data.MealRepository
import com.rawtracker.data.NoopHealthSync
import com.rawtracker.data.FileStore
import com.rawtracker.db.RawTrackerDb
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val ENVELOPE = """
{"candidates":[{"content":{"parts":[{"text":"{\"food_name\":\"Greek Yogurt + Berries\",\"calories\":210,\"protein_g\":18,\"carbs_g\":24,\"fat_g\":4}"}]}}]}
"""

private class MemFileStore : FileStore {
    override fun saveImage(bytes: ByteArray): String = "mem://img"
    override fun readImage(path: String): ByteArray? = null
    override fun exportCsv(fileName: String, content: String): String = "mem://$fileName"
}

/** End-to-end of the core pipeline: AI parse (mocked transport) -> persist -> daily totals. */
class ParseAndSaveFlowTest {
    @Test
    fun parsedFoodIsPersistedAndShowsUpInTodayTotals() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        RawTrackerDb.Schema.create(driver)
        val repo = MealRepository(RawTrackerDb(driver), NoopHealthSync(), MemFileStore())

        val engine = MockEngine {
            respond(
                content = ENVELOPE,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = GeminiClient(
            apiKey = "test-key",
            httpClient = HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        )

        val parsed = client.parse(text = "greek yogurt with berries", imageBytes = null)
        assertTrue(parsed.isSuccess, "parse failed: ${parsed.exceptionOrNull()}")

        repo.saveMeal(parsed.getOrThrow(), System.currentTimeMillis(), null)

        val totals = repo.observeTodayTotals().first()
        assertEquals(210, totals.calories)
        assertEquals(18, totals.protein)
        assertEquals(24, totals.carbs)
        assertEquals(4, totals.fat)

        val meals = repo.observeTodayMeals().first()
        assertEquals(1, meals.size)
        assertEquals("Greek Yogurt + Berries", meals.first().foodName)

        driver.close()
    }
}
