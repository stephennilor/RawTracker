package com.rawtracker

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Single shared container so Swift (URL handling, widgets) and Compose share state. */
object IosApp {
    val container: AppContainer by lazy { createIosContainer() }

    /** Called from Swift on `rawtracker://capture` deep links (e.g. the camera widget). */
    fun onDeepLinkCapture() {
        container.cameraRequest.value += 1
    }

    /**
     * Called from Swift on `rawtracker://water` deep links (e.g. the progress widget).
     * Opens the water chooser (amounts + custom + time) so adding water gives visible
     * feedback and amount choice, matching the "+ FOOD" flow — rather than silently logging.
     */
    fun onDeepLinkWater() {
        container.openWaterRequest.value += 1
    }
}

fun MainViewController(): UIViewController = ComposeUIViewController {
    App(IosApp.container)
}
