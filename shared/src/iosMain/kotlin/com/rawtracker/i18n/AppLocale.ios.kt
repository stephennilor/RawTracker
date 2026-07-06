package com.rawtracker.i18n

import platform.Foundation.NSBundle

actual fun platformLanguageTag(): String =
    (NSBundle.mainBundle.preferredLocalizations.firstOrNull() as? String) ?: "en"
