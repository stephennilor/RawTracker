package com.rawtracker.widget

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import java.util.Locale

internal data class WidgetStrings(
    val kcal: String,
    val protein: String,
    val carbs: String,
    val fat: String,
    val food: String,
    val water: String,
    val caloriesDescription: (value: Int, goal: Int?) -> String,
    val macrosDescription: (protein: Int, carbs: Int, fat: Int) -> String,
    val addFoodDescription: String,
    val addWaterDescription: String,
)

internal fun widgetStrings(context: Context): WidgetStrings =
    when (widgetLanguage(context)) {
        "pl" -> WidgetStrings(
            kcal = "KCAL",
            protein = "B",
            carbs = "W",
            fat = "T",
            food = "JEDZ",
            water = "H\u2082O",
            caloriesDescription = { value, goal ->
                if (goal != null && goal > 0) "$value z $goal kalorii" else "$value kalorii"
            },
            macrosDescription = { protein, carbs, fat ->
                "Makro: białko $protein, węgle $carbs, tłuszcz $fat"
            },
            addFoodDescription = "Dodaj jedzenie",
            addWaterDescription = "Dodaj wodę",
        )
        else -> WidgetStrings(
            kcal = "KCAL",
            protein = "P",
            carbs = "C",
            fat = "F",
            food = "FOOD",
            water = "H\u2082O",
            caloriesDescription = { value, goal ->
                if (goal != null && goal > 0) "$value of $goal calories" else "$value calories"
            },
            macrosDescription = { protein, carbs, fat ->
                "Macros: $protein protein, $carbs carbs, $fat fat"
            },
            addFoodDescription = "Add food",
            addWaterDescription = "Add water",
        )
    }

private fun widgetLanguage(context: Context): String {
    val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(LocaleManager::class.java)
            ?.applicationLocales
            ?.takeUnless { it.isEmpty }
            ?.get(0)
            ?.toLanguageTag()
    } else {
        null
    } ?: context.resources.configuration.locales.get(0)?.toLanguageTag()
        ?: Locale.getDefault().toLanguageTag()
    return tag.substringBefore('-').lowercase(Locale.ROOT)
}
