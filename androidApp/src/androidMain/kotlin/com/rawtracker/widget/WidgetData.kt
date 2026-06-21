package com.rawtracker.widget

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.rawtracker.data.HealthMeal
import com.rawtracker.data.HealthWater
import com.rawtracker.db.RawTrackerDb
import java.time.LocalDate
import java.time.ZoneId

data class WidgetTotals(
    val cal: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val water: Int,
    val goalCal: Int,
    val goalProtein: Int,
    val goalCarbs: Int,
    val goalFat: Int,
    val canvas: Long,
    val ink: Long,
    val showMacros: Boolean,
    val showGoal: Boolean,
    val showWater: Boolean,
    val showFood: Boolean
)

private fun dayBounds(): Pair<Long, Long> {
    val zone = ZoneId.systemDefault()
    val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
    return start to start + 24L * 60 * 60 * 1000
}

/** Start/end epoch-ms for the current day, for callers outside this file. */
fun todayBounds(): Pair<Long, Long> = dayBounds()

/** Reads today's macro totals + goals + widget prefs straight from the shared SQLite DB. */
object WidgetData {
    fun load(context: Context): WidgetTotals {
        val driver = AndroidSqliteDriver(RawTrackerDb.Schema, context.applicationContext, "rawtracker.db")
        return try {
            val q = RawTrackerDb(driver).rawTrackerQueries
            val (start, end) = dayBounds()
            val totals = q.dayTotals(start, end).executeAsOne()
            val water = q.waterTotalBetween(start, end).executeAsOne()
            val goals = q.allGoals().executeAsList().associate { it.key to it.value_ }
            fun flag(key: String) = goals[key]?.toBooleanStrictOrNull() ?: true
            WidgetTotals(
                cal = totals.calories.toInt(),
                protein = totals.protein.toInt(),
                carbs = totals.carbs.toInt(),
                fat = totals.fat.toInt(),
                water = water.toInt(),
                goalCal = goals["cal"]?.toIntOrNull() ?: 2500,
                goalProtein = goals["protein"]?.toIntOrNull() ?: 165,
                goalCarbs = goals["carbs"]?.toIntOrNull() ?: 250,
                goalFat = goals["fat"]?.toIntOrNull() ?: 80,
                canvas = goals["canvas"]?.toLongOrNull() ?: 0xFFFFE9CE,
                ink = goals["ink"]?.toLongOrNull() ?: 0xFF8A53FF,
                showMacros = flag("w_show_macros"),
                showGoal = flag("w_show_goal"),
                showWater = flag("w_show_water"),
                showFood = flag("w_show_food")
            )
        } finally {
            driver.close()
        }
    }

    /** Logs water straight into the shared DB so widget taps appear in app history. */
    fun logWater(context: Context, milliliters: Int, at: Long) {
        val driver = AndroidSqliteDriver(RawTrackerDb.Schema, context.applicationContext, "rawtracker.db")
        try {
            RawTrackerDb(driver).rawTrackerQueries.insertWater(milliliters.toLong(), at)
        } finally {
            driver.close()
        }
    }

    /** Reads a day's meals + water from the shared DB as a health-hub reconcile payload. */
    fun healthPayload(context: Context, start: Long, end: Long): Pair<List<HealthMeal>, List<HealthWater>> {
        val driver = AndroidSqliteDriver(RawTrackerDb.Schema, context.applicationContext, "rawtracker.db")
        return try {
            val q = RawTrackerDb(driver).rawTrackerQueries
            val meals = q.mealsBetween(start, end) { id, name, cal, p, c, f, eatenAt, _, _ ->
                HealthMeal(name, cal.toInt(), p.toInt(), c.toInt(), f.toInt(), eatenAt, "rawtracker_meal_$id")
            }.executeAsList()
            val waters = q.waterBetween(start, end) { id, ml, loggedAt ->
                HealthWater(ml.toInt(), loggedAt, "rawtracker_water_$id")
            }.executeAsList()
            meals to waters
        } finally {
            driver.close()
        }
    }
}
