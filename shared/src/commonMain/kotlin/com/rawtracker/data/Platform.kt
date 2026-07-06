package com.rawtracker.data

import app.cash.sqldelight.db.SqlDriver

/** Provides a platform-specific SQLDelight driver. */
interface SqlDriverFactory {
    fun create(): SqlDriver
}

/** Local file persistence for captured images and CSV exports. */
interface FileStore {
    /** Persists raw image bytes and returns an absolute local path. */
    fun saveImage(bytes: ByteArray): String

    /** Loads previously saved image bytes, or null if missing. */
    fun readImage(path: String): ByteArray?

    /** Writes CSV text to a local file and returns its absolute path. */
    fun exportCsv(fileName: String, content: String): String
}

/** Reports whether the device currently has a network connection. */
interface Connectivity {
    fun isOnline(): Boolean

    /**
     * Invokes [onOnline] whenever connectivity is (re)gained, so queued work can drain.
     * Implementations that cannot observe changes may simply never call back.
     */
    fun registerOnlineListener(onOnline: () -> Unit)
}

/** A meal exactly as it should appear in the native health hub. */
data class HealthMeal(
    val foodName: String,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val atEpochMillis: Long,
    /** Stable id used for idempotent upserts. */
    val clientId: String,
    val endEpochMillis: Long? = null
)

/** A glass of water exactly as it should appear in the native health hub. */
data class HealthWater(
    val milliliters: Int,
    val atEpochMillis: Long,
    val clientId: String,
    val endEpochMillis: Long? = null
)

fun dailyHealthMeals(dayStartMillis: Long, dayEndMillis: Long, meals: List<HealthMeal>): List<HealthMeal> {
    if (meals.isEmpty()) return emptyList()
    return listOf(
        HealthMeal(
            foodName = "RawTracker daily total",
            calories = meals.sumOf { it.calories },
            proteinG = meals.sumOf { it.proteinG },
            carbsG = meals.sumOf { it.carbsG },
            fatG = meals.sumOf { it.fatG },
            atEpochMillis = dayStartMillis,
            clientId = "rawtracker_nutrition_day_$dayStartMillis",
            endEpochMillis = dayEndMillis
        )
    )
}

fun dailyHealthWaters(dayStartMillis: Long, dayEndMillis: Long, waters: List<HealthWater>): List<HealthWater> {
    if (waters.isEmpty()) return emptyList()
    return listOf(
        HealthWater(
            milliliters = waters.sumOf { it.milliliters },
            atEpochMillis = dayStartMillis,
            clientId = "rawtracker_hydration_day_$dayStartMillis",
            endEpochMillis = dayEndMillis
        )
    )
}

enum class HealthSyncStatus {
    Synced,
    Unavailable,
    MissingPermissions,
    Failed
}

data class HealthSyncResult(
    val status: HealthSyncStatus,
    val message: String? = null
) {
    val synced: Boolean get() = status == HealthSyncStatus.Synced

    companion object {
        val Synced = HealthSyncResult(HealthSyncStatus.Synced)
        val Unavailable = HealthSyncResult(HealthSyncStatus.Unavailable)
        val MissingPermissions = HealthSyncResult(HealthSyncStatus.MissingPermissions)
        fun failed(message: String? = null) = HealthSyncResult(HealthSyncStatus.Failed, message)
    }
}

/**
 * Bridge to the native health hub (Health Connect / HealthKit).
 *
 * The single mutation primitive is [reconcileDay]: it makes the hub MATCH local truth for a day,
 * which keeps adds, edits, deletes and time-changes (on any date, including historical) correct
 * without fragile per-record id bookkeeping — both platforms only allow deleting records this app
 * authored, so rewriting a day cleanly replaces our own contribution.
 */
interface HealthSync {
    suspend fun hasPermissions(): Boolean
    suspend fun requestPermissions(): Boolean

    /**
     * Makes the hub match local truth for the half-open window [dayStartMillis, dayEndMillis):
     * deletes THIS app's nutrition + hydration records in that window, then inserts [meals] and
     * [waters]. A graceful no-op when the hub is unavailable or permission is missing.
     */
    suspend fun reconcileDay(
        dayStartMillis: Long,
        dayEndMillis: Long,
        meals: List<HealthMeal>,
        waters: List<HealthWater>
    ): HealthSyncResult
}

/** Default no-op used when no native health hub is wired in. */
class NoopHealthSync : HealthSync {
    override suspend fun hasPermissions(): Boolean = false
    override suspend fun requestPermissions(): Boolean = false
    override suspend fun reconcileDay(
        dayStartMillis: Long,
        dayEndMillis: Long,
        meals: List<HealthMeal>,
        waters: List<HealthWater>
    ): HealthSyncResult = HealthSyncResult.Unavailable
}
