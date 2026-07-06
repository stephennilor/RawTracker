package com.rawtracker.i18n

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month

expect fun platformLanguageTag(): String

enum class AppLanguage {
    English,
    Polish
}

fun appLanguageFor(tag: String): AppLanguage =
    if (tag.lowercase().startsWith("pl")) AppLanguage.Polish else AppLanguage.English

fun appStringsFor(tag: String): AppStrings =
    when (appLanguageFor(tag)) {
        AppLanguage.Polish -> PolishStrings
        AppLanguage.English -> EnglishStrings
    }

val strings: AppStrings
    get() = appStringsFor(platformLanguageTag())

interface AppStrings {
    val rawtracker: String
    val queuedSuffix: String
    val logWater: String
    val settings: String
    val nothingLoggedToday: String
    val photoAttached: String
    val removePhoto: String
    val addFood: String
    val describe: String
    val photo: String
    val pickPhotos: String
    val previousDay: String
    val nextDay: String
    val kcal: String
    val proteinShort: String
    val carbsShort: String
    val fatShort: String
    val waterShort: String
    val delete: String
    val camera: String
    val pickPhoto: String
    val foodPlaceholder: String
    val dictate: String
    val dictationPrompt: String
    val dictationNoSpeech: String
    val dictationUnavailable: String
    val dictationPermissionDenied: String
    val dictationUseKeyboardMic: String
    val send: String
    val editEntry: String
    val reviewAndSave: String
    val food: String
    val caloriesShort: String
    val protein: String
    val carbs: String
    val fat: String
    val realityCheck: String
    val modelBreakdown: String
    val portionMultiplier: String
    val time: String
    val cancel: String
    val update: String
    val save: String
    val fallbackFoodName: String
    val settingsTitle: String
    val back: String
    val dailyTargets: String
    val calories: String
    val waterTarget: String
    val saveTargets: String
    val duotone: String
    val customColours: String
    val livePreview: String
    val livePreviewUnsaved: String
    val primaryInk: String
    val secondaryCanvas: String
    val randomiseShort: String
    val hexCode: String
    val applyColours: String
    val discardColours: String
    val widget: String
    val widgetHelp: String
    val calorieGoal: String
    val macrosPcf: String
    val foodButton: String
    val waterButton: String
    val healthSync: String
    val healthConnectedHelp: String
    val healthWriteOnlyHelp: String
    val healthConnected: String
    val connectHealth: String
    val healthResyncHelp: String
    val resyncToday: String
    val resyncAll: String
    val geminiApiKey: String
    val builtInKeyHelp: String
    val saveKey: String
    val clear: String
    val getFreeKeyHelp: String
    val data: String
    val exportCsv: String
    val build: String
    val addWater: String
    val customMl: String
    val exampleWaterAmount: String
    val add: String
    val previousMonth: String
    val nextMonth: String
    val jumpToday: String
    val askingGemini: String
    val elapsedLabel: String
    val parseQuickWait: String
    val parsePhotoWait: String
    val parseRetryWait: String
    val parseLongWait: String
    val done: String

    fun queued(count: Long): String = "$count $queuedSuffix"
    fun photosAttached(count: Int): String
    fun waterToday(total: Int): String
    fun waterAmount(ml: Int): String
    fun waterLogged(ml: Int): String
    fun mealMacros(calories: Int, protein: Int, carbs: Int, fat: Int): String
    fun itemBreakdown(name: String, grams: Int, calories: Int, protein: Int, carbs: Int, fat: Int): String
    fun customKeyActive(last4: String): String
    fun exported(path: String): String
    fun elapsed(value: String): String = "$elapsedLabel $value"
    fun monthAbbrev(month: Month): String
    fun monthFull(month: Month): String
    fun weekdayAbbrev(day: DayOfWeek): String
    fun weekdayInitials(): List<String>
    fun todayLabel(): String

    val healthUnavailableOrDenied: String
    val healthConnectedAndSynced: String
    val healthConnectedNeedsAttention: String
    val resyncedToday: String
    val resyncedAll: String
    val connectHealthFirst: String
    val healthUnavailable: String
    val healthSyncFailed: String
    val offlineQueued: String
    val parseFailed: String
    val cancelled: String
    val updated: String
    val logged: String
    val clearedBuiltInKey: String
    val apiKeySaved: String
    val exportFailed: String
    val missingGeminiKey: String
    val provideFoodInput: String
    val cannotReachGemini: String
    val geminiTimeout: String
    val geminiEmptyEstimate: String
    val geminiGarbled: String
    val geminiUnexpected: String
    val geminiKeyRejected: String
    val geminiKeyDenied: String
    val geminiRateLimit: String
    val geminiModelUnavailable: String
    val geminiBusy: String
    val geminiServerGlitch: String
    fun geminiRejected(status: Int): String
    fun geminiReturnedError(status: Int): String
}

object EnglishStrings : AppStrings {
    override val rawtracker = "RAWTRACKER"
    override val queuedSuffix = "queued"
    override val logWater = "Log water"
    override val settings = "Settings"
    override val nothingLoggedToday = "// nothing logged today"
    override val photoAttached = "photo attached"
    override val removePhoto = "Remove photo"
    override val addFood = "ADD FOOD"
    override val describe = "Describe"
    override val photo = "Photo"
    override val pickPhotos = "Pick photo(s)"
    override val previousDay = "Previous day"
    override val nextDay = "Next day"
    override val kcal = "KCAL"
    override val proteinShort = "P"
    override val carbsShort = "C"
    override val fatShort = "F"
    override val waterShort = "H\u2082O"
    override val delete = "Delete"
    override val camera = "Camera"
    override val pickPhoto = "Pick photo"
    override val foodPlaceholder = "food..."
    override val dictate = "Dictate"
    override val dictationPrompt = "Say what you ate"
    override val dictationNoSpeech = "I didn't catch anything. Tap the mic and try again."
    override val dictationUnavailable = "Dictation isn't available on this device."
    override val dictationPermissionDenied = "Microphone permission was denied. Enable it in system settings to dictate meals."
    override val dictationUseKeyboardMic = "Use the keyboard mic on iOS to dictate into the food field."
    override val send = "Send"
    override val editEntry = "EDIT ENTRY"
    override val reviewAndSave = "REVIEW & SAVE"
    override val food = "FOOD"
    override val caloriesShort = "CAL"
    override val protein = "PROTEIN"
    override val carbs = "CARBS"
    override val fat = "FAT"
    override val realityCheck = "REALITY CHECK"
    override val modelBreakdown = "MODEL BREAKDOWN"
    override val portionMultiplier = "portion multiplier"
    override val time = "TIME"
    override val cancel = "Cancel"
    override val update = "Update"
    override val save = "Save"
    override val fallbackFoodName = "Food"
    override val settingsTitle = "SETTINGS"
    override val back = "Back"
    override val dailyTargets = "DAILY TARGETS"
    override val calories = "CALORIES"
    override val waterTarget = "WATER (ml)"
    override val saveTargets = "Save targets"
    override val duotone = "DUOTONE"
    override val customColours = "CUSTOM COLOURS"
    override val livePreview = "live preview"
    override val livePreviewUnsaved = "live preview - unsaved"
    override val primaryInk = "PRIMARY / INK"
    override val secondaryCanvas = "SECONDARY / CANVAS"
    override val randomiseShort = "RND"
    override val hexCode = "HEX"
    override val applyColours = "Set custom"
    override val discardColours = "Discard"
    override val widget = "WIDGET"
    override val widgetHelp = "// choose what the home-screen widget can show (size permitting)"
    override val calorieGoal = "Calorie goal"
    override val macrosPcf = "Macros (P/C/F)"
    override val foodButton = "+ Food button"
    override val waterButton = "+ Water button"
    override val healthSync = "HEALTH SYNC"
    override val healthConnectedHelp = "// connected - meals + water mirror to health"
    override val healthWriteOnlyHelp = "// write-only: calories, macros, water"
    override val healthConnected = "Health connected"
    override val connectHealth = "Connect Health"
    override val healthResyncHelp = "// re-sync rewrites Health from this app's data (fixes drift from old edits/deletes)"
    override val resyncToday = "Re-sync today"
    override val resyncAll = "Re-sync all"
    override val geminiApiKey = "GEMINI API KEY"
    override val builtInKeyHelp = "// using built-in key - paste your own to use your quota"
    override val saveKey = "Save key"
    override val clear = "Clear"
    override val getFreeKeyHelp = "// get a free key at aistudio.google.com/app/apikey"
    override val data = "DATA"
    override val exportCsv = "Export CSV"
    override val build = "build"
    override val addWater = "ADD WATER"
    override val customMl = "CUSTOM (ml)"
    override val exampleWaterAmount = "e.g. 330"
    override val add = "Add"
    override val previousMonth = "Previous month"
    override val nextMonth = "Next month"
    override val jumpToday = "Jump to today"
    override val askingGemini = "ASKING GEMINI"
    override val elapsedLabel = "Elapsed"
    override val parseQuickWait = "Request sent. Waiting for model response."
    override val parsePhotoWait = "Still waiting. Gemini can be slow with photos."
    override val parseRetryWait = "Still working. You can cancel and retry if needed."
    override val parseLongWait = "Long wait. Network or Gemini may be slow right now."
    override val done = "Done"
    override val healthUnavailableOrDenied = "Health unavailable or permission was denied."
    override val healthConnectedAndSynced = "Health connected and synced."
    override val healthConnectedNeedsAttention = "Health connected, but sync needs attention."
    override val resyncedToday = "Re-synced today to Health."
    override val resyncedAll = "Re-synced all days to Health."
    override val connectHealthFirst = "Connect Health first, then try again."
    override val healthUnavailable = "Health Connect is unavailable on this device."
    override val healthSyncFailed = "Health sync failed. Try Re-sync in Settings, then check Health permissions."
    override val offlineQueued = "Offline. Queued for later."
    override val parseFailed = "Parse failed"
    override val cancelled = "Cancelled."
    override val updated = "Updated."
    override val logged = "Logged."
    override val clearedBuiltInKey = "Cleared - using built-in key."
    override val apiKeySaved = "API key saved."
    override val exportFailed = "Export failed"
    override val missingGeminiKey = "Add your Gemini API key in Settings to parse food."
    override val provideFoodInput = "Type what you ate or attach a photo."
    override val cannotReachGemini = "Can't reach Gemini. Check your internet connection and try again."
    override val geminiTimeout = "Gemini took too long. Try again with a shorter note or a smaller photo."
    override val geminiEmptyEstimate = "Gemini couldn't estimate this meal. Try a clearer photo or add a few words describing it."
    override val geminiGarbled = "Gemini sent back a garbled answer. Tap send again - if it keeps happening, rephrase or shorten your note."
    override val geminiUnexpected = "Got an unexpected response from Gemini. Try again in a moment."
    override val geminiKeyRejected = "Your Gemini API key isn't accepted. In Settings, paste a current key from Google AI Studio (older unrestricted keys are now blocked)."
    override val geminiKeyDenied = "Gemini denied this API key. In Google AI Studio, check the key is active and that the Gemini API (and billing, if required) is enabled."
    override val geminiRateLimit = "Gemini rate limit hit. Wait a minute, then try again - or check your quota in Google AI Studio."
    override val geminiModelUnavailable = "This Gemini model isn't available for your key. Try again later or update the app."
    override val geminiBusy = "Gemini is busy right now. Wait a moment and try again."
    override val geminiServerGlitch = "Gemini hit a server glitch. Try again in a minute."

    override fun waterToday(total: Int) = "today: $total ml"
    override fun photosAttached(count: Int) =
        if (count == 1) "photo attached" else "$count photos attached"
    override fun waterAmount(ml: Int) = "$ml ml"
    override fun waterLogged(ml: Int) = "+$ml ml water"
    override fun mealMacros(calories: Int, protein: Int, carbs: Int, fat: Int) =
        "${calories}kcal  P$protein C$carbs F$fat"
    override fun itemBreakdown(name: String, grams: Int, calories: Int, protein: Int, carbs: Int, fat: Int) =
        "$name - ${grams}g - ${calories}kcal P$protein C$carbs F$fat"
    override fun customKeyActive(last4: String) = "// custom key active (....$last4)"
    override fun exported(path: String) = "Exported: $path"
    override fun geminiRejected(status: Int) = "Gemini rejected the request ($status). Check your API key in Settings."
    override fun geminiReturnedError(status: Int) = "Gemini returned an error ($status). Try again in a moment."
    override fun monthAbbrev(month: Month) = when (month) {
        Month.JANUARY -> "JAN"; Month.FEBRUARY -> "FEB"; Month.MARCH -> "MAR"
        Month.APRIL -> "APR"; Month.MAY -> "MAY"; Month.JUNE -> "JUN"
        Month.JULY -> "JUL"; Month.AUGUST -> "AUG"; Month.SEPTEMBER -> "SEP"
        Month.OCTOBER -> "OCT"; Month.NOVEMBER -> "NOV"; Month.DECEMBER -> "DEC"
    }
    override fun monthFull(month: Month) = when (month) {
        Month.JANUARY -> "JANUARY"; Month.FEBRUARY -> "FEBRUARY"; Month.MARCH -> "MARCH"
        Month.APRIL -> "APRIL"; Month.MAY -> "MAY"; Month.JUNE -> "JUNE"
        Month.JULY -> "JULY"; Month.AUGUST -> "AUGUST"; Month.SEPTEMBER -> "SEPTEMBER"
        Month.OCTOBER -> "OCTOBER"; Month.NOVEMBER -> "NOVEMBER"; Month.DECEMBER -> "DECEMBER"
    }
    override fun weekdayAbbrev(day: DayOfWeek) = when (day) {
        DayOfWeek.MONDAY -> "MON"; DayOfWeek.TUESDAY -> "TUE"; DayOfWeek.WEDNESDAY -> "WED"
        DayOfWeek.THURSDAY -> "THU"; DayOfWeek.FRIDAY -> "FRI"; DayOfWeek.SATURDAY -> "SAT"
        DayOfWeek.SUNDAY -> "SUN"
    }
    override fun weekdayInitials() = listOf("M", "T", "W", "T", "F", "S", "S")
    override fun todayLabel() = "TODAY"
}

object PolishStrings : AppStrings {
    override val rawtracker = "RAWTRACKER"
    override val queuedSuffix = "w kolejce"
    override val logWater = "Dodaj wodę"
    override val settings = "Ustawienia"
    override val nothingLoggedToday = "// dziś nic nie zapisano"
    override val photoAttached = "zdjęcie dodane"
    override val removePhoto = "Usuń zdjęcie"
    override val addFood = "DODAJ JEDZENIE"
    override val describe = "Opisz"
    override val photo = "Zdjęcie"
    override val pickPhotos = "Wybierz zdjęcia"
    override val previousDay = "Poprzedni dzień"
    override val nextDay = "Następny dzień"
    override val kcal = "KCAL"
    override val proteinShort = "B"
    override val carbsShort = "W"
    override val fatShort = "T"
    override val waterShort = "H\u2082O"
    override val delete = "Usuń"
    override val camera = "Aparat"
    override val pickPhoto = "Wybierz zdjęcie"
    override val foodPlaceholder = "jedzenie..."
    override val dictate = "Dyktuj"
    override val dictationPrompt = "Powiedz, co zjadłeś"
    override val dictationNoSpeech = "Nic nie usłyszałem. Dotknij mikrofonu i spróbuj ponownie."
    override val dictationUnavailable = "Dyktowanie nie jest dostępne na tym urządzeniu."
    override val dictationPermissionDenied = "Odmówiono dostępu do mikrofonu. Włącz go w ustawieniach systemu, aby dyktować posiłki."
    override val dictationUseKeyboardMic = "Na iOS użyj mikrofonu klawiatury, aby dyktować w polu jedzenia."
    override val send = "Wyślij"
    override val editEntry = "EDYTUJ WPIS"
    override val reviewAndSave = "SPRAWDŹ I ZAPISZ"
    override val food = "JEDZENIE"
    override val caloriesShort = "KCAL"
    override val protein = "BIAŁKO"
    override val carbs = "WĘGLE"
    override val fat = "TŁUSZCZ"
    override val realityCheck = "KONTROLA REALNOŚCI"
    override val modelBreakdown = "ROZBICIE MODELU"
    override val portionMultiplier = "mnożnik porcji"
    override val time = "CZAS"
    override val cancel = "Anuluj"
    override val update = "Aktualizuj"
    override val save = "Zapisz"
    override val fallbackFoodName = "Jedzenie"
    override val settingsTitle = "USTAWIENIA"
    override val back = "Wstecz"
    override val dailyTargets = "CELE DZIENNE"
    override val calories = "KALORIE"
    override val waterTarget = "WODA (ml)"
    override val saveTargets = "Zapisz cele"
    override val duotone = "DUOTON"
    override val customColours = "WŁASNE KOLORY"
    override val livePreview = "podgląd na żywo"
    override val livePreviewUnsaved = "podgląd na żywo - niezapisany"
    override val primaryInk = "PODSTAWOWY / TUSZ"
    override val secondaryCanvas = "DRUGI / TŁO"
    override val randomiseShort = "LOS"
    override val hexCode = "HEX"
    override val applyColours = "Ustaw własne"
    override val discardColours = "Odrzuć"
    override val widget = "WIDŻET"
    override val widgetHelp = "// wybierz, co widżet na ekranie głównym może pokazać"
    override val calorieGoal = "Cel kalorii"
    override val macrosPcf = "Makro (B/W/T)"
    override val foodButton = "+ Przycisk jedzenia"
    override val waterButton = "+ Przycisk wody"
    override val healthSync = "SYNCHRONIZACJA HEALTH"
    override val healthConnectedHelp = "// połączono - posiłki + woda trafiają do Health"
    override val healthWriteOnlyHelp = "// tylko zapis: kalorie, makro, woda"
    override val healthConnected = "Health połączone"
    override val connectHealth = "Połącz Health"
    override val healthResyncHelp = "// ponowna synchronizacja nadpisuje Health danymi z aplikacji"
    override val resyncToday = "Synch. dziś"
    override val resyncAll = "Synch. wszystko"
    override val geminiApiKey = "KLUCZ API GEMINI"
    override val builtInKeyHelp = "// używasz klucza wbudowanego - wklej własny, aby użyć swojego limitu"
    override val saveKey = "Zapisz klucz"
    override val clear = "Wyczyść"
    override val getFreeKeyHelp = "// darmowy klucz: aistudio.google.com/app/apikey"
    override val data = "DANE"
    override val exportCsv = "Eksport CSV"
    override val build = "build"
    override val addWater = "DODAJ WODĘ"
    override val customMl = "WŁASNE (ml)"
    override val exampleWaterAmount = "np. 330"
    override val add = "Dodaj"
    override val previousMonth = "Poprzedni miesiąc"
    override val nextMonth = "Następny miesiąc"
    override val jumpToday = "Przejdź do dziś"
    override val askingGemini = "PYTAM GEMINI"
    override val elapsedLabel = "Czas"
    override val parseQuickWait = "Wysłano. Czekam na odpowiedź modelu."
    override val parsePhotoWait = "Nadal czekam. Gemini bywa wolne przy zdjęciach."
    override val parseRetryWait = "Nadal pracuję. Możesz anulować i spróbować ponownie."
    override val parseLongWait = "Długie oczekiwanie. Sieć albo Gemini mogą być teraz wolne."
    override val done = "Gotowe"
    override val healthUnavailableOrDenied = "Health niedostępne albo odmówiono uprawnień."
    override val healthConnectedAndSynced = "Health połączone i zsynchronizowane."
    override val healthConnectedNeedsAttention = "Health połączone, ale synchronizacja wymaga uwagi."
    override val resyncedToday = "Dzisiejsze dane ponownie wysłane do Health."
    override val resyncedAll = "Wszystkie dni ponownie wysłane do Health."
    override val connectHealthFirst = "Najpierw połącz Health, potem spróbuj ponownie."
    override val healthUnavailable = "Health Connect jest niedostępne na tym urządzeniu."
    override val healthSyncFailed = "Synchronizacja Health nie powiodła się. Sprawdź uprawnienia Health."
    override val offlineQueued = "Offline. Dodano do kolejki."
    override val parseFailed = "Analiza nie powiodła się"
    override val cancelled = "Anulowano."
    override val updated = "Zaktualizowano."
    override val logged = "Zapisano."
    override val clearedBuiltInKey = "Wyczyszczono - używam klucza wbudowanego."
    override val apiKeySaved = "Klucz API zapisany."
    override val exportFailed = "Eksport nie powiódł się"
    override val missingGeminiKey = "Dodaj klucz API Gemini w Ustawieniach, aby analizować jedzenie."
    override val provideFoodInput = "Wpisz, co zjadłeś, albo dodaj zdjęcie."
    override val cannotReachGemini = "Nie mogę połączyć się z Gemini. Sprawdź internet i spróbuj ponownie."
    override val geminiTimeout = "Gemini odpowiadało zbyt długo. Spróbuj krótszej notatki albo mniejszego zdjęcia."
    override val geminiEmptyEstimate = "Gemini nie potrafiło oszacować tego posiłku. Spróbuj wyraźniejszego zdjęcia albo dodaj krótki opis."
    override val geminiGarbled = "Gemini zwróciło nieczytelną odpowiedź. Wyślij ponownie; jeśli to wraca, skróć albo przeformułuj opis."
    override val geminiUnexpected = "Gemini zwróciło nieoczekiwaną odpowiedź. Spróbuj za chwilę."
    override val geminiKeyRejected = "Twój klucz API Gemini nie został przyjęty. W Ustawieniach wklej aktualny klucz z Google AI Studio."
    override val geminiKeyDenied = "Gemini odrzuciło ten klucz API. Sprawdź w Google AI Studio, czy klucz, API Gemini i ewentualne rozliczenia są aktywne."
    override val geminiRateLimit = "Limit Gemini został osiągnięty. Poczekaj minutę albo sprawdź limit w Google AI Studio."
    override val geminiModelUnavailable = "Ten model Gemini jest niedostępny dla Twojego klucza. Spróbuj później albo zaktualizuj aplikację."
    override val geminiBusy = "Gemini jest teraz zajęte. Poczekaj chwilę i spróbuj ponownie."
    override val geminiServerGlitch = "Gemini miało problem serwera. Spróbuj za minutę."

    override fun waterToday(total: Int) = "dziś: $total ml"
    override fun photosAttached(count: Int) =
        if (count == 1) "zdjęcie dodane" else "$count zdjęcia dodane"
    override fun waterAmount(ml: Int) = "$ml ml"
    override fun waterLogged(ml: Int) = "+$ml ml wody"
    override fun mealMacros(calories: Int, protein: Int, carbs: Int, fat: Int) =
        "${calories}kcal  B$protein W$carbs T$fat"
    override fun itemBreakdown(name: String, grams: Int, calories: Int, protein: Int, carbs: Int, fat: Int) =
        "$name - ${grams}g - ${calories}kcal B$protein W$carbs T$fat"
    override fun customKeyActive(last4: String) = "// własny klucz aktywny (....$last4)"
    override fun exported(path: String) = "Wyeksportowano: $path"
    override fun geminiRejected(status: Int) = "Gemini odrzuciło żądanie ($status). Sprawdź klucz API w Ustawieniach."
    override fun geminiReturnedError(status: Int) = "Gemini zwróciło błąd ($status). Spróbuj za chwilę."
    override fun monthAbbrev(month: Month) = when (month) {
        Month.JANUARY -> "STY"; Month.FEBRUARY -> "LUT"; Month.MARCH -> "MAR"
        Month.APRIL -> "KWI"; Month.MAY -> "MAJ"; Month.JUNE -> "CZE"
        Month.JULY -> "LIP"; Month.AUGUST -> "SIE"; Month.SEPTEMBER -> "WRZ"
        Month.OCTOBER -> "PAŹ"; Month.NOVEMBER -> "LIS"; Month.DECEMBER -> "GRU"
    }
    override fun monthFull(month: Month) = when (month) {
        Month.JANUARY -> "STYCZEŃ"; Month.FEBRUARY -> "LUTY"; Month.MARCH -> "MARZEC"
        Month.APRIL -> "KWIECIEŃ"; Month.MAY -> "MAJ"; Month.JUNE -> "CZERWIEC"
        Month.JULY -> "LIPIEC"; Month.AUGUST -> "SIERPIEŃ"; Month.SEPTEMBER -> "WRZESIEŃ"
        Month.OCTOBER -> "PAŹDZIERNIK"; Month.NOVEMBER -> "LISTOPAD"; Month.DECEMBER -> "GRUDZIEŃ"
    }
    override fun weekdayAbbrev(day: DayOfWeek) = when (day) {
        DayOfWeek.MONDAY -> "PON"; DayOfWeek.TUESDAY -> "WTO"; DayOfWeek.WEDNESDAY -> "ŚRO"
        DayOfWeek.THURSDAY -> "CZW"; DayOfWeek.FRIDAY -> "PIĄ"; DayOfWeek.SATURDAY -> "SOB"
        DayOfWeek.SUNDAY -> "NIE"
    }
    override fun weekdayInitials() = listOf("P", "W", "Ś", "C", "P", "S", "N")
    override fun todayLabel() = "DZIŚ"
}
