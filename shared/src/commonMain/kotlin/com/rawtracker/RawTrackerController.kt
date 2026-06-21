package com.rawtracker

import com.rawtracker.data.DuotonePrefs
import com.rawtracker.data.Goals
import com.rawtracker.data.HistoryItem
import com.rawtracker.data.MacroTotals
import com.rawtracker.data.Meal
import com.rawtracker.data.ParsedFood
import com.rawtracker.data.WidgetPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

enum class Screen { Input, Settings }

data class UiState(
    val screen: Screen = Screen.Input,
    val input: String = "",
    val attachedImage: ByteArray? = null,
    val isParsing: Boolean = false,
    val draft: ParsedFood? = null,
    val draftTimestamp: Long = 0L,
    val draftImagePath: String? = null,
    val message: String? = null,
    val pendingCount: Long = 0L,
    val healthConnected: Boolean = false,
    val showWaterSheet: Boolean = false,
    /** When non-null, the review sheet is editing this existing meal rather than creating one. */
    val editingMealId: Long? = null,
    /** Shows the widget-triggered chooser: describe / photo / photo + describe. */
    val showAddChooser: Boolean = false
)

@OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
class RawTrackerController(
    private val container: AppContainer,
    private val scope: CoroutineScope
) {
    private val repo = container.repository

    /** Build identifier surfaced in Settings so you can confirm the running build. */
    val appVersion: String = container.appVersion

    private fun today(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    /** The day currently being viewed/edited. Defaults to today. */
    private val _selectedDate = MutableStateFlow(today())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    val history: StateFlow<List<HistoryItem>> =
        _selectedDate.flatMapLatest { repo.observeHistory(it) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())
    val waterTotal: StateFlow<Int> =
        _selectedDate.flatMapLatest { repo.observeWaterTotal(it) }
            .stateIn(scope, SharingStarted.Eagerly, 0)
    val totals: StateFlow<MacroTotals> =
        _selectedDate.flatMapLatest { repo.observeTotals(it) }
            .stateIn(scope, SharingStarted.Eagerly, MacroTotals())
    val goals: StateFlow<Goals> =
        repo.observeGoals().stateIn(scope, SharingStarted.Eagerly, Goals())
    val duotone: StateFlow<DuotonePrefs> =
        repo.observeDuotone().stateIn(scope, SharingStarted.Eagerly, DuotonePrefs())
    val widgetPrefs: StateFlow<WidgetPrefs> =
        repo.observeWidgetPrefs().stateIn(scope, SharingStarted.Eagerly, WidgetPrefs())
    /** User-supplied Gemini key ("" means the built-in fallback key is used). */
    val apiKey: StateFlow<String> =
        repo.observeApiKey().stateIn(scope, SharingStarted.Eagerly, "")

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var parseJob: Job? = null
    /** When set, focus the composer (and keyboard) once the next photo is attached. */
    private var focusAfterPhoto = false

    /** Signal from deep links (camera widget) to launch the camera once. */
    val cameraRequest: StateFlow<Int> = container.cameraRequest

    /** Bumped to request that the input composer take focus (and raise the keyboard). */
    private val _focusInput = MutableStateFlow(0)
    val focusInput: StateFlow<Int> = _focusInput.asStateFlow()

    init {
        scope.launch { refreshPendingCount() }
        scope.launch { refreshHealthStatus() }
        // Self-heal: on every launch make Health Connect match local truth for today, so stale
        // entries from interrupted edits/deletes (or older app versions) get corrected. No-op
        // without permission.
        scope.launch { runCatching { repo.reconcileHealthToday() } }
        retryPending()
        container.connectivity.registerOnlineListener { retryPending() }
        // Water deep links (e.g. iOS widget) bump this signal; log a glass on each bump.
        scope.launch {
            container.waterRequest.collect { n -> if (n > 0) logWater() }
        }
        // The widget "+ FOOD" button bumps this; present the add-food chooser.
        scope.launch {
            container.addFoodRequest.collect { n ->
                if (n > 0) _ui.update { it.copy(screen = Screen.Input, showAddChooser = true) }
            }
        }
        // The widget "+ H₂O" button bumps this; present the water chooser (amounts + custom + time).
        scope.launch {
            container.openWaterRequest.collect { n ->
                if (n > 0) _ui.update { it.copy(screen = Screen.Input, showWaterSheet = true) }
            }
        }
    }

    // ---- Add-food chooser (from widget) ---------------------------------------
    fun dismissAddChooser() = _ui.update { it.copy(showAddChooser = false) }

    /** Text-only entry: close the chooser and raise the composer/keyboard. */
    fun chooseDescribe() {
        _ui.update { it.copy(showAddChooser = false) }
        _focusInput.update { it + 1 }
    }

    /** Photo-only entry: close the chooser and open the camera. */
    fun choosePhoto() {
        _ui.update { it.copy(showAddChooser = false) }
        container.cameraRequest.value += 1
    }

    /** Photo + text: open the camera, then focus the composer once the shot is attached. */
    fun choosePhotoAndDescribe() {
        focusAfterPhoto = true
        _ui.update { it.copy(showAddChooser = false) }
        container.cameraRequest.value += 1
    }

    fun connectHealth() = scope.launch {
        val granted = runCatching { container.healthSync.requestPermissions() }.getOrDefault(false)
        _ui.update {
            it.copy(
                healthConnected = granted,
                message = if (granted) "Health connected." else "Health unavailable or denied."
            )
        }
    }

    private suspend fun refreshHealthStatus() {
        val connected = runCatching { container.healthSync.hasPermissions() }.getOrDefault(false)
        _ui.update { it.copy(healthConnected = connected) }
    }

    /** Rewrites today's Health Connect data from local truth (manual heal). */
    fun resyncHealthToday() = scope.launch {
        runCatching { repo.reconcileHealthToday() }
        _ui.update { it.copy(message = "Re-synced today to Health.") }
    }

    /** Rewrites every day present in the local DB into Health Connect (full heal). */
    fun resyncHealthAll() = scope.launch {
        runCatching { repo.reconcileHealthAll() }
        _ui.update { it.copy(message = "Re-synced all days to Health.") }
    }

    fun openSettings() = _ui.update { it.copy(screen = Screen.Settings) }
    fun openInput() = _ui.update { it.copy(screen = Screen.Input) }

    // ---- Day navigation -------------------------------------------------------
    fun selectDate(date: LocalDate) { _selectedDate.value = date }
    fun previousDay() { _selectedDate.value = _selectedDate.value.minus(DatePeriod(days = 1)) }
    fun nextDay() { _selectedDate.value = _selectedDate.value.plus(DatePeriod(days = 1)) }
    fun goToday() { _selectedDate.value = today() }
    fun isViewingToday(): Boolean = _selectedDate.value == today()

    /** Default timestamp for a new entry: the selected day at the current wall-clock time. */
    fun newEntryTimestamp(): Long = timestampOnSelectedDay()

    /** Epoch (ms) on the currently selected day, keeping the present wall-clock time. */
    private fun timestampOnSelectedDay(): Long {
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now().toLocalDateTime(tz)
        val d = _selectedDate.value
        return LocalDateTime(d.year, d.monthNumber, d.dayOfMonth, now.hour, now.minute, now.second)
            .toInstant(tz).toEpochMilliseconds()
    }
    fun onInputChange(text: String) = _ui.update { it.copy(input = text) }
    fun attachImage(bytes: ByteArray) {
        _ui.update { it.copy(attachedImage = bytes) }
        if (focusAfterPhoto) {
            focusAfterPhoto = false
            _focusInput.update { it + 1 }
        }
    }
    fun clearAttachment() = _ui.update { it.copy(attachedImage = null) }
    fun dismissSheet() = _ui.update { it.copy(draft = null, draftImagePath = null, editingMealId = null) }
    fun clearMessage() = _ui.update { it.copy(message = null) }

    /** Opens the review sheet pre-filled with an existing meal so it can be edited in place. */
    fun editMeal(meal: Meal) = _ui.update {
        it.copy(
            draft = ParsedFood(meal.foodName, meal.calories, meal.proteinG, meal.carbsG, meal.fatG),
            draftTimestamp = meal.eatenAt,
            draftImagePath = meal.imagePath,
            editingMealId = meal.id
        )
    }

    fun submit() {
        val state = _ui.value
        val text = state.input.trim().ifBlank { null }
        val image = state.attachedImage
        if (text == null && image == null) return

        if (!container.connectivity.isOnline()) {
            scope.launch {
                val path = image?.let { repo.saveImageBytes(it) }
                repo.enqueuePending(text, path)
                refreshPendingCount()
                _ui.update {
                    it.copy(input = "", attachedImage = null, message = "Offline. Queued for later.")
                }
            }
            return
        }

        _ui.update { it.copy(isParsing = true, message = null) }
        parseJob = scope.launch {
            val result = container.gemini.parse(text, image)
            if (!isActive) return@launch
            result.fold(
                onSuccess = { food ->
                    val imagePath = image?.let { repo.saveImageBytes(it) }
                    _ui.update {
                        it.copy(
                            isParsing = false,
                            input = "",
                            attachedImage = null,
                            draft = food,
                            draftTimestamp = timestampOnSelectedDay(),
                            draftImagePath = imagePath
                        )
                    }
                },
                onFailure = { err ->
                    _ui.update {
                        it.copy(isParsing = false, message = err.message ?: "Parse failed")
                    }
                }
            )
        }
    }

    /** Cancels an in-flight parse and unlocks the UI. */
    fun cancelParse() {
        parseJob?.cancel()
        parseJob = null
        _ui.update { it.copy(isParsing = false, message = "Cancelled.") }
    }

    fun confirmSave(edited: ParsedFood, timestamp: Long) {
        val editingId = _ui.value.editingMealId
        val imagePath = _ui.value.draftImagePath
        scope.launch {
            if (editingId != null) repo.updateMeal(editingId, edited, timestamp)
            else repo.saveMeal(edited, timestamp, imagePath)
            _ui.update {
                it.copy(
                    draft = null,
                    draftImagePath = null,
                    editingMealId = null,
                    message = if (editingId != null) "Updated." else "Logged."
                )
            }
            container.onDataChanged()
        }
    }

    fun deleteMeal(id: Long) = scope.launch {
        repo.deleteMeal(id)
        container.onDataChanged()
    }

    fun openWaterSheet() = _ui.update { it.copy(showWaterSheet = true) }
    fun closeWaterSheet() = _ui.update { it.copy(showWaterSheet = false) }

    fun logWater(ml: Int = 250) = logWaterAt(ml, timestampOnSelectedDay())

    fun logWaterAt(ml: Int, epochMs: Long) = scope.launch {
        if (ml <= 0) return@launch
        repo.logWater(ml, epochMs)
        _ui.update { it.copy(message = "+$ml ml water", showWaterSheet = false) }
        container.onDataChanged()
    }

    fun deleteWater(id: Long) = scope.launch {
        repo.deleteWater(id)
        container.onDataChanged()
    }

    fun updateMealTime(id: Long, epochMs: Long) = scope.launch {
        repo.updateMealTime(id, epochMs)
        container.onDataChanged()
    }

    fun updateWaterTime(id: Long, epochMs: Long) = scope.launch {
        repo.updateWaterTime(id, epochMs)
        container.onDataChanged()
    }

    fun saveGoals(goals: Goals) = scope.launch { repo.saveGoals(goals) }
    fun saveDuotone(prefs: DuotonePrefs) = scope.launch { repo.saveDuotone(prefs) }

    fun saveWidgetPrefs(prefs: WidgetPrefs) = scope.launch {
        repo.saveWidgetPrefs(prefs)
        container.onDataChanged()
    }

    fun saveApiKey(key: String) = scope.launch {
        repo.saveApiKey(key)
        _ui.update {
            it.copy(message = if (key.isBlank()) "Cleared \u2014 using built-in key." else "API key saved.")
        }
    }

    fun exportCsv() = scope.launch {
        val path = runCatching { repo.exportCsv() }.getOrNull()
        _ui.update { it.copy(message = path?.let { p -> "Exported: $p" } ?: "Export failed") }
    }

    fun retryPending() {
        if (!container.connectivity.isOnline()) return
        scope.launch {
            repo.drainPending { _, text, imagePath ->
                val image = imagePath?.let { runCatching { repo.readImage(it) }.getOrNull() }
                val res = container.gemini.parse(text, image)
                res.fold(
                    onSuccess = { food ->
                        repo.saveMeal(food, Clock.System.now().toEpochMilliseconds(), imagePath)
                        true
                    },
                    onFailure = { false }
                )
            }
            refreshPendingCount()
            container.onDataChanged()
        }
    }

    private suspend fun refreshPendingCount() {
        val count = repo.pendingCount()
        _ui.update { it.copy(pendingCount = count) }
    }
}
