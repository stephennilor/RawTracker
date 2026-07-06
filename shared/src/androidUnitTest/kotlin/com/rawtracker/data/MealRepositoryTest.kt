package com.rawtracker.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.rawtracker.db.RawTrackerDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** In-memory FileStore so CSV/image paths can be asserted without touching disk. */
private class FakeFileStore : FileStore {
    var lastCsvName: String? = null
    var lastCsvContent: String? = null
    private val images = mutableMapOf<String, ByteArray>()
    private var counter = 0

    override fun saveImage(bytes: ByteArray): String {
        val path = "mem://image-${counter++}"
        images[path] = bytes
        return path
    }

    override fun readImage(path: String): ByteArray? = images[path]

    override fun exportCsv(fileName: String, content: String): String {
        lastCsvName = fileName
        lastCsvContent = content
        return "mem://$fileName"
    }
}

/** Records the latest reconciled day payload so we can assert the health mirror fires. */
private class RecordingHealthSync : HealthSync {
    val lastMeals = mutableListOf<HealthMeal>()
    val lastWaters = mutableListOf<HealthWater>()
    val calls = mutableListOf<Pair<List<HealthMeal>, List<HealthWater>>>()
    var result: HealthSyncResult = HealthSyncResult.Synced

    override suspend fun hasPermissions(): Boolean = true
    override suspend fun requestPermissions(): Boolean = true
    override suspend fun reconcileDay(
        dayStartMillis: Long,
        dayEndMillis: Long,
        meals: List<HealthMeal>,
        waters: List<HealthWater>
    ): HealthSyncResult {
        calls += meals to waters
        lastMeals.clear(); lastMeals.addAll(meals)
        lastWaters.clear(); lastWaters.addAll(waters)
        return result
    }
}

class MealRepositoryTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: RawTrackerDb
    private lateinit var fileStore: FakeFileStore
    private lateinit var health: RecordingHealthSync
    private lateinit var repo: MealRepository

    private fun now() = System.currentTimeMillis()
    private fun dateOf(epochMs: Long) =
        Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault()).date

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        RawTrackerDb.Schema.create(driver)
        db = RawTrackerDb(driver)
        fileStore = FakeFileStore()
        health = RecordingHealthSync()
        repo = MealRepository(db, health, fileStore)
    }

    @AfterTest
    fun teardown() {
        driver.close()
    }

    @Test
    fun savingMealsAccumulatesTodayTotalsAndMirrorsToHealth() = runTest {
        repo.saveMeal(ParsedFood("Eggs", 200, 18, 2, 14), now(), null)
        repo.saveMeal(ParsedFood("Oats", 300, 10, 54, 6), now(), null)

        val totals = repo.observeTodayTotals().first()
        assertEquals(500, totals.calories)
        assertEquals(28, totals.protein)
        assertEquals(56, totals.carbs)
        assertEquals(20, totals.fat)

        // Both of today's meals were pushed to the health hub on the last reconcile.
        assertEquals(2, health.lastMeals.size)
        assertEquals(2, health.calls.size)
        assertTrue(repo.observeTodayMeals().first().all { it.syncedHealth })
    }

    @Test
    fun healthReconcileFiresForMealEditsDeletesAndTimeChanges() = runTest {
        val id = repo.saveMeal(ParsedFood("Toast", 250, 8, 30, 10), now(), null)
        assertEquals(1, health.lastMeals.size)

        repo.updateMeal(id, ParsedFood("Big Toast", 400, 12, 50, 18), now())
        assertEquals("Big Toast", health.lastMeals.single().foodName)
        assertEquals(400, health.lastMeals.single().calories)

        repo.updateMealTime(id, now())
        assertEquals(1, health.lastMeals.size)

        repo.deleteMeal(id)
        assertEquals(0, health.lastMeals.size)
        assertTrue(health.calls.size >= 4)
    }

    @Test
    fun healthReconcileFiresForWaterAddsDeletesAndTimeChanges() = runTest {
        val ts = now()
        repo.logWater(330, ts)
        assertEquals(1, health.lastWaters.size)
        assertEquals(330, health.lastWaters.single().milliliters)

        val water = repo.observeWater(dateOf(ts)).first().single()
        repo.updateWaterTime(water.id, ts + 60_000)
        assertEquals(1, health.lastWaters.size)

        repo.deleteWater(water.id)
        assertEquals(0, health.lastWaters.size)
    }

    @Test
    fun failedHealthReconcileDoesNotMarkMealSynced() = runTest {
        health.result = HealthSyncResult.MissingPermissions

        repo.saveMeal(ParsedFood("Eggs", 200, 18, 2, 14), now(), null)

        assertEquals(1, health.calls.size)
        assertTrue(repo.observeTodayMeals().first().none { it.syncedHealth })
    }

    @Test
    fun mealOutsideTodayIsExcludedFromTotals() = runTest {
        val twoDaysAgo = now() - 2L * 24 * 60 * 60 * 1000
        repo.saveMeal(ParsedFood("Old Pizza", 800, 30, 90, 35), twoDaysAgo, null)
        repo.saveMeal(ParsedFood("Today Salad", 150, 5, 12, 9), now(), null)

        val totals = repo.observeTodayTotals().first()
        assertEquals(150, totals.calories)
    }

    @Test
    fun goalsRoundTripWithDefaults() = runTest {
        assertEquals(Goals(), repo.observeGoals().first())

        repo.saveGoals(Goals(calories = 3000, protein = 200, carbs = 300, fat = 90))
        val saved = repo.observeGoals().first()
        assertEquals(3000, saved.calories)
        assertEquals(200, saved.protein)
        assertEquals(300, saved.carbs)
        assertEquals(90, saved.fat)
    }

    @Test
    fun duotonePrefsRoundTrip() = runTest {
        assertEquals(DuotonePrefs(), repo.observeDuotone().first())

        repo.saveDuotone(DuotonePrefs(canvas = 0xFF101010, ink = 0xFFEEFF00))
        val saved = repo.observeDuotone().first()
        assertEquals(0xFF101010, saved.canvas)
        assertEquals(0xFFEEFF00, saved.ink)
    }

    @Test
    fun csvExportEscapesQuotesAndIncludesHeader() = runTest {
        repo.saveMeal(ParsedFood("\"Fancy\" Toast", 250, 8, 30, 10), 1_700_000_000_000L, null)

        val path = repo.exportCsv()
        assertEquals("mem://rawtracker_export.csv", path)
        val csv = fileStore.lastCsvContent ?: error("no csv written")
        assertTrue(csv.startsWith("id,food_name,calories,protein_g,carbs_g,fat_g,eaten_at_epoch_ms\n"))
        assertContainsLine(csv, "\"\"Fancy\"\" Toast")
        assertContainsLine(csv, "250,8,30,10,1700000000000")
    }

    @Test
    fun offlineQueueEnqueuesCountsAndDrains() = runTest {
        assertEquals(0L, repo.pendingCount())

        repo.enqueuePending("late night tacos", null)
        repo.enqueuePending(null, "mem://photo")
        assertEquals(2L, repo.pendingCount())

        val handled = mutableListOf<String?>()
        repo.drainPending { _, text, _ ->
            handled += text
            true
        }
        assertEquals(0L, repo.pendingCount())
        assertEquals(listOf("late night tacos", null), handled)
    }

    @Test
    fun drainStopsOnFailureAndKeepsRemaining() = runTest {
        repo.enqueuePending("first", null)
        repo.enqueuePending("second", null)

        repo.drainPending { _, _, _ -> false }

        assertEquals(2L, repo.pendingCount())
    }

    @Test
    fun savedImageBytesAreReadableBack() {
        val path = repo.saveImageBytes(byteArrayOf(9, 8, 7))
        val read = repo.readImage(path)
        assertEquals(listOf<Byte>(9, 8, 7), read?.toList())
        assertNull(repo.readImage("mem://missing"))
    }

    private fun assertContainsLine(csv: String, fragment: String) {
        assertTrue(csv.contains(fragment), "CSV did not contain '$fragment':\n$csv")
    }
}
