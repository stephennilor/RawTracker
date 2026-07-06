package com.rawtracker.i18n

import java.util.Locale

actual fun platformLanguageTag(): String = Locale.getDefault().toLanguageTag()
