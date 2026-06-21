package com.rawtracker.data

import kotlinx.serialization.Serializable

/**
 * Strict shape returned by Gemini. Field names match the JSON schema enforced in the request.
 */
@Serializable
data class ParsedFood(
    val food_name: String,
    val calories: Int,
    val protein_g: Int,
    val carbs_g: Int,
    val fat_g: Int
)

data class Meal(
    val id: Long,
    val foodName: String,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val eatenAt: Long,
    val imagePath: String?,
    val syncedHealth: Boolean
)

data class WaterLog(
    val id: Long,
    val milliliters: Int,
    val loggedAt: Long
)

/** A single row in the day's timeline: either a logged meal or a glass of water. */
sealed interface HistoryItem {
    val timestamp: Long

    data class MealEntry(val meal: Meal) : HistoryItem {
        override val timestamp: Long get() = meal.eatenAt
    }

    data class WaterEntry(val water: WaterLog) : HistoryItem {
        override val timestamp: Long get() = water.loggedAt
    }
}

data class MacroTotals(
    val calories: Int = 0,
    val protein: Int = 0,
    val carbs: Int = 0,
    val fat: Int = 0
)

data class Goals(
    val calories: Int = 2500,
    val protein: Int = 165,
    val carbs: Int = 250,
    val fat: Int = 80
)

/** Two-variable duotone palette stored as ARGB ints. */
data class DuotonePrefs(
    val canvas: Long = 0xFFFFE9CE,
    val ink: Long = 0xFF8A53FF
)

/** Which elements the home-screen widget is allowed to show (size permitting). */
data class WidgetPrefs(
    val showMacros: Boolean = true,
    val showGoal: Boolean = true,
    val showWater: Boolean = true,
    val showFood: Boolean = true
)
