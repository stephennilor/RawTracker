package com.rawtracker

import com.rawtracker.ai.GeminiClient
import com.rawtracker.config.BuildKonfig
import com.rawtracker.data.Connectivity
import com.rawtracker.data.FileStore
import com.rawtracker.data.HealthSync
import com.rawtracker.data.MealRepository
import com.rawtracker.data.SqlDriverFactory
import com.rawtracker.db.RawTrackerDb
import kotlinx.coroutines.flow.MutableStateFlow

/** Manual dependency container wired per platform and handed to [App]. */
class AppContainer(
    driverFactory: SqlDriverFactory,
    fileStore: FileStore,
    val healthSync: HealthSync,
    val connectivity: Connectivity,
    /** Human-readable build identifier, shown in Settings to confirm which build is running. */
    val appVersion: String = "dev",
    geminiApiKey: String = BuildKonfig.GEMINI_API_KEY,
    /** Invoked after any meal/water change so platforms can refresh home-screen widgets. */
    var onDataChanged: () -> Unit = {}
) {
    private val db = RawTrackerDb(driverFactory.create())
    val repository = MealRepository(db, healthSync, fileStore)

    // Bring-your-own-key: a user key from Settings wins; otherwise fall back to the build-time key.
    val gemini = GeminiClient(apiKeyProvider = {
        repository.getApiKey().ifBlank { geminiApiKey }
    })

    /** Bumped by platform deep links (e.g. the 1x1 camera widget) to auto-open the camera. */
    val cameraRequest = MutableStateFlow(0)

    /** Bumped by the widget "+ FOOD" button to present the add-food chooser. */
    val addFoodRequest = MutableStateFlow(0)

    /** Bumped by platform deep links (e.g. the iOS progress widget) to log a glass of water. */
    val waterRequest = MutableStateFlow(0)

    /** Bumped by the widget "+ H₂O" button to present the water chooser (amounts + custom + time). */
    val openWaterRequest = MutableStateFlow(0)
}
