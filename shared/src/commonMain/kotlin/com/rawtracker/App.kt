package com.rawtracker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.rawtracker.design.RawTrackerTheme
import com.rawtracker.ui.InputScreen
import com.rawtracker.ui.ParseSheet
import com.rawtracker.ui.SettingsScreen
import com.rawtracker.ui.WaterSheet

@Composable
fun App(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val controller = remember { RawTrackerController(container, scope) }
    val duotone by controller.duotone.collectAsState()

    RawTrackerTheme(prefs = duotone) {
        val ui by controller.ui.collectAsState()
        when (ui.screen) {
            Screen.Input -> InputScreen(controller)
            Screen.Settings -> SettingsScreen(controller)
        }
        if (ui.draft != null) {
            ParseSheet(controller)
        }
        if (ui.showWaterSheet) {
            WaterSheet(controller)
        }
    }
}
