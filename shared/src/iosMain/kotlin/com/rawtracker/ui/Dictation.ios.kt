package com.rawtracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.rawtracker.i18n.strings

@Composable
actual fun rememberDictationLauncher(
    onResult: (String) -> Unit,
    onError: (String) -> Unit
): DictationLauncher {
    val currentOnError by rememberUpdatedState(onError)
    val currentStrings = strings
    return DictationLauncher {
        currentOnError(currentStrings.dictationUseKeyboardMic)
    }
}
