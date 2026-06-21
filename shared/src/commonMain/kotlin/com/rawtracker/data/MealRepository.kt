package com.rawtracker.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.rawtracker.db.RawTrackerDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn

private const val API_KEY_KEY = "gemini_api_key"

@OptIn(ExperimentalTime::class)
class MealRepository(
    private val db: RawTrackerDb,
    private val healthSync: HealthSync,
    private val fileStore: FileStore
) {
    private val q get() = db.rawTrackerQueries

    private fun boundsFor(date: LocalDate): Pair<Long, Long> {
        val tz = TimeZone.currentSystemDefault()
        val start = date.atStartOfDayIn(tz).toEpochMilliseconds()
        val end = start + 24L * 60 * 60 * 1000
        return start to end
    }

    private fun today(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    private fun dayOf(epochMs: Long): LocalDate =
        Instant.fromEpochMilliseconds(epochMs)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date

    private fun mealClientId(id: Long) = "rawtracker_meal_$id"
    private fun waterClientId(id: Long) = "rawtracker_water_$id"

    /**
     * Pushes local truth for [date] into the health hub: it deletes this app's own records for the
     * day and rewrites them. Called after every mutation so adds/edits/deletes/time-changes stay in
     * sync on ANY date (including historical days). Best-effort: failures (no permission, hub
     * unavailable) are swallowed so the local write always succeeds.
     */
    private suspend fun reconcileHealthDay(date: LocalDate) {
        val (start, end) = boundsFor(date)
        val meals = q.mealsBetween(start, end, ::mapMeal).executeAsList().map {
            HealthMeal(it.foodName, it.calories, it.proteinG, it.carbsG, it.fatG, it.eatenAt, mealClientId(it.id))
        }
        val waters = q.waterBetween(start, end) { id, milliliters, loggedAt ->
            HealthWater(milliliters.toInt(), loggedAt, waterClientId(id))
        }.executeAsList()
        val ok = runCatching { healthSync.reconcileDay(start, end, meals, waters) }.isSuccess
        if (ok) runCatching { q.markSyncedBetween(start, end) }
    }

    /** Reconciles one calendar day on demand (used by launch self-heal / Settings re-sync). */
    suspend fun reconcileHealthForDay(date: LocalDate) =
        withContext(Dispatchers.Default) { reconcileHealthDay(date) }

    suspend fun reconcileHealthToday() = reconcileHealthForDay(today())

    /** Reconciles every day that has any local meal or water (full re-sync). */
    suspend fun reconcileHealthAll() = withContext(Dispatchers.Default) {
        val mealDays = q.allMeals(::mapMeal).executeAsList().map { dayOf(it.eatenAt) }
        val waterDays = q.allWater { _, _, loggedAt -> dayOf(loggedAt) }.executeAsList()
        (mealDays + waterDays).toSet().sorted().forEach { reconcileHealthDay(it) }
    }

    fun observeTodayMeals(): Flow<List<Meal>> = observeMeals(today())
    fun observeTodayTotals(): Flow<MacroTotals> = observeTotals(today())

    fun observeMeals(date: LocalDate): Flow<List<Meal>> {
        val (start, end) = boundsFor(date)
        return q.mealsBetween(start, end, ::mapMeal)
            .asFlow().mapToList(Dispatchers.Default)
    }

    fun observeWater(date: LocalDate): Flow<List<WaterLog>> {
        val (start, end) = boundsFor(date)
        return q.waterBetween(start, end) { id, milliliters, loggedAt ->
            WaterLog(id = id, milliliters = milliliters.toInt(), loggedAt = loggedAt)
        }.asFlow().mapToList(Dispatchers.Default)
    }

    /** Unified, time-sorted timeline of meals and water for a given day. */
    fun observeHistory(date: LocalDate): Flow<List<HistoryItem>> =
        combine(observeMeals(date), observeWater(date)) { meals, waters ->
            val items = meals.map { HistoryItem.MealEntry(it) } +
                waters.map { HistoryItem.WaterEntry(it) }
            items.sortedByDescending { it.timestamp }
        }

    fun observeWaterTotal(date: LocalDate): Flow<Int> {
        val (start, end) = boundsFor(date)
        return q.waterTotalBetween(start, end).asFlow().mapToOne(Dispatchers.Default)
            .map { it.toInt() }
    }

    fun observeTotals(date: LocalDate): Flow<MacroTotals> {
        val (start, end) = boundsFor(date)
        return q.dayTotals(start, end).asFlow().mapToOne(Dispatchers.Default)
            .map { MacroTotals(it.calories.toInt(), it.protein.toInt(), it.carbs.toInt(), it.fat.toInt()) }
    }

    suspend fun todayTotalsOnce(): MacroTotals = withContext(Dispatchers.Default) {
        val (start, end) = boundsFor(today())
        val t = q.dayTotals(start, end).executeAsOne()
        MacroTotals(t.calories.toInt(), t.protein.toInt(), t.carbs.toInt(), t.fat.toInt())
    }

    suspend fun updateMealTime(id: Long, eatenAt: Long) = withContext(Dispatchers.Default) {
        val old = q.mealById(id, ::mapMeal).executeAsOneOrNull()
        q.updateMealTime(eatenAt, id)
        // A time change can move an entry across the midnight boundary, so heal both days.
        setOfNotNull(old?.let { dayOf(it.eatenAt) }, dayOf(eatenAt)).forEach { reconcileHealthDay(it) }
    }

    suspend fun updateWaterTime(id: Long, loggedAt: Long) = withContext(Dispatchers.Default) {
        val old = q.waterById(id) { _, _, lg -> lg }.executeAsOneOrNull()
        q.updateWaterTime(loggedAt, id)
        setOfNotNull(old?.let { dayOf(it) }, dayOf(loggedAt)).forEach { reconcileHealthDay(it) }
    }

    /** Edits a meal's name/macros/time in one shot, then reconciles the affected day(s). */
    suspend fun updateMeal(id: Long, food: ParsedFood, eatenAt: Long) = withContext(Dispatchers.Default) {
        val old = q.mealById(id, ::mapMeal).executeAsOneOrNull()
        q.updateMeal(
            foodName = food.food_name,
            calories = food.calories.toLong(),
            proteinG = food.protein_g.toLong(),
            carbsG = food.carbs_g.toLong(),
            fatG = food.fat_g.toLong(),
            eatenAt = eatenAt,
            id = id
        )
        setOfNotNull(old?.let { dayOf(it.eatenAt) }, dayOf(eatenAt)).forEach { reconcileHealthDay(it) }
    }

    suspend fun goalsOnce(): Goals = withContext(Dispatchers.Default) {
        val m = q.allGoals().executeAsList().associate { it.key to it.value_ }
        Goals(
            calories = m["cal"]?.toIntOrNull() ?: 2500,
            protein = m["protein"]?.toIntOrNull() ?: 165,
            carbs = m["carbs"]?.toIntOrNull() ?: 250,
            fat = m["fat"]?.toIntOrNull() ?: 80
        )
    }

    fun observeGoals(): Flow<Goals> =
        q.allGoals().asFlow().mapToList(Dispatchers.Default).map { rows ->
            val m = rows.associate { it.key to it.value_ }
            Goals(
                calories = m["cal"]?.toIntOrNull() ?: 2500,
                protein = m["protein"]?.toIntOrNull() ?: 165,
                carbs = m["carbs"]?.toIntOrNull() ?: 250,
                fat = m["fat"]?.toIntOrNull() ?: 80
            )
        }

    fun observeDuotone(): Flow<DuotonePrefs> =
        q.allGoals().asFlow().mapToList(Dispatchers.Default).map { rows ->
            val m = rows.associate { it.key to it.value_ }
            DuotonePrefs(
                canvas = m["canvas"]?.toLongOrNull() ?: 0xFFFFE9CE,
                ink = m["ink"]?.toLongOrNull() ?: 0xFF8A53FF
            )
        }

    /** Reads the user-supplied Gemini API key (empty string when unset). */
    suspend fun getApiKey(): String = withContext(Dispatchers.Default) {
        q.selectGoal(API_KEY_KEY).executeAsOneOrNull().orEmpty()
    }

    fun observeApiKey(): Flow<String> =
        q.selectGoal(API_KEY_KEY).asFlow().mapToOneOrNull(Dispatchers.Default).map { it.orEmpty() }

    suspend fun saveApiKey(key: String) = withContext(Dispatchers.Default) {
        q.upsertGoal(API_KEY_KEY, key.trim())
    }

    suspend fun saveGoals(goals: Goals) = withContext(Dispatchers.Default) {
        q.transaction {
            q.upsertGoal("cal", goals.calories.toString())
            q.upsertGoal("protein", goals.protein.toString())
            q.upsertGoal("carbs", goals.carbs.toString())
            q.upsertGoal("fat", goals.fat.toString())
        }
    }

    suspend fun saveDuotone(prefs: DuotonePrefs) = withContext(Dispatchers.Default) {
        q.transaction {
            q.upsertGoal("canvas", prefs.canvas.toString())
            q.upsertGoal("ink", prefs.ink.toString())
        }
    }

    fun observeWidgetPrefs(): Flow<WidgetPrefs> =
        q.allGoals().asFlow().mapToList(Dispatchers.Default).map { rows ->
            val m = rows.associate { it.key to it.value_ }
            fun flag(key: String) = m[key]?.toBooleanStrictOrNull() ?: true
            WidgetPrefs(
                showMacros = flag("w_show_macros"),
                showGoal = flag("w_show_goal"),
                showWater = flag("w_show_water"),
                showFood = flag("w_show_food")
            )
        }

    suspend fun saveWidgetPrefs(prefs: WidgetPrefs) = withContext(Dispatchers.Default) {
        q.transaction {
            q.upsertGoal("w_show_macros", prefs.showMacros.toString())
            q.upsertGoal("w_show_goal", prefs.showGoal.toString())
            q.upsertGoal("w_show_water", prefs.showWater.toString())
            q.upsertGoal("w_show_food", prefs.showFood.toString())
        }
    }

    /** Inserts a meal and reconciles that day so the health hub matches local truth. */
    suspend fun saveMeal(food: ParsedFood, eatenAt: Long, imagePath: String?): Long =
        withContext(Dispatchers.Default) {
            val id = q.transactionWithResult {
                q.insertMeal(
                    food_name = food.food_name,
                    calories = food.calories.toLong(),
                    protein_g = food.protein_g.toLong(),
                    carbs_g = food.carbs_g.toLong(),
                    fat_g = food.fat_g.toLong(),
                    eaten_at = eatenAt,
                    image_path = imagePath,
                    synced_health = 0
                )
                q.lastInsertedMealId().executeAsOne()
            }
            reconcileHealthDay(dayOf(eatenAt))
            id
        }

    suspend fun deleteMeal(id: Long) = withContext(Dispatchers.Default) {
        val meal = q.mealById(id, ::mapMeal).executeAsOneOrNull()
        q.deleteMeal(id)
        meal?.let { reconcileHealthDay(dayOf(it.eatenAt)) }
    }

    /** Records water locally (so it shows in history) and reconciles its day to the health hub. */
    suspend fun logWater(milliliters: Int, at: Long = Clock.System.now().toEpochMilliseconds()) =
        withContext(Dispatchers.Default) {
            q.insertWater(milliliters.toLong(), at)
            reconcileHealthDay(dayOf(at))
        }

    suspend fun deleteWater(id: Long) = withContext(Dispatchers.Default) {
        val loggedAt = q.waterById(id) { _, _, lg -> lg }.executeAsOneOrNull()
        q.deleteWater(id)
        loggedAt?.let { reconcileHealthDay(dayOf(it)) }
    }

    suspend fun exportCsv(): String = withContext(Dispatchers.Default) {
        val rows = q.allMeals(::mapMeal).executeAsList()
        val sb = StringBuilder("id,food_name,calories,protein_g,carbs_g,fat_g,eaten_at_epoch_ms\n")
        rows.forEach { m ->
            val name = m.foodName.replace("\"", "\"\"")
            sb.append("${m.id},\"$name\",${m.calories},${m.proteinG},${m.carbsG},${m.fatG},${m.eatenAt}\n")
        }
        fileStore.exportCsv("rawtracker_export.csv", sb.toString())
    }

    // Offline queue ------------------------------------------------------------
    suspend fun enqueuePending(textPrompt: String?, imagePath: String?) =
        withContext(Dispatchers.Default) {
            q.insertPending(textPrompt, imagePath, Clock.System.now().toEpochMilliseconds())
        }

    suspend fun pendingCount(): Long = withContext(Dispatchers.Default) {
        q.countPending().executeAsOne()
    }

    suspend fun drainPending(handler: suspend (id: Long, text: String?, imagePath: String?) -> Boolean) =
        withContext(Dispatchers.Default) {
            val items = q.allPending().executeAsList()
            for (item in items) {
                val ok = handler(item.id, item.text_prompt, item.image_path)
                if (ok) q.deletePending(item.id) else break
            }
        }

    fun saveImageBytes(bytes: ByteArray): String = fileStore.saveImage(bytes)

    fun readImage(path: String): ByteArray? = fileStore.readImage(path)

    private fun mapMeal(
        id: Long,
        food_name: String,
        calories: Long,
        protein_g: Long,
        carbs_g: Long,
        fat_g: Long,
        eaten_at: Long,
        image_path: String?,
        synced_health: Long
    ): Meal = Meal(
        id = id,
        foodName = food_name,
        calories = calories.toInt(),
        proteinG = protein_g.toInt(),
        carbsG = carbs_g.toInt(),
        fatG = fat_g.toInt(),
        eatenAt = eaten_at,
        imagePath = image_path,
        syncedHealth = synced_health == 1L
    )
}
