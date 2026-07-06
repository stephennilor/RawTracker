package com.rawtracker.ui

import androidx.compose.runtime.Composable

class DictationLauncher(val launch: () -> Unit)

@Composable
expect fun rememberDictationLauncher(
    onResult: (String) -> Unit,
    onError: (String) -> Unit
): DictationLauncher
