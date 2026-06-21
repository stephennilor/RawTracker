package com.rawtracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.updateAll
import androidx.health.connect.client.PermissionController
import com.rawtracker.widget.CameraWidget
import com.rawtracker.widget.ProgressWidget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var pendingPermissionResult: CompletableDeferred<Set<String>>? = null
    private lateinit var permissionLauncher: ActivityResultLauncher<Set<String>>
    private lateinit var container: AppContainer
    private val appScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val healthSync = AndroidHealthSync(applicationContext)
        permissionLauncher = registerForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) { granted ->
            pendingPermissionResult?.complete(granted)
            pendingPermissionResult = null
        }
        healthSync.permissionLauncher = { perms ->
            val deferred = CompletableDeferred<Set<String>>()
            pendingPermissionResult = deferred
            permissionLauncher.launch(perms)
            deferred.await()
        }

        container = createAndroidContainer(
            context = applicationContext,
            healthSync = healthSync,
            appVersion = BuildConfig.VERSION_NAME,
            onDataChanged = { refreshWidgets() }
        )
        handleDeepLink(intent)
        setContent {
            App(container)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "rawtracker") return
        when (data.host) {
            "capture" -> container.cameraRequest.value += 1
            "add" -> container.addFoodRequest.value += 1
            "water" -> container.openWaterRequest.value += 1
        }
    }

    private fun refreshWidgets() {
        appScope.launch {
            runCatching {
                val widgets: List<GlanceAppWidget> = listOf(ProgressWidget(), CameraWidget())
                widgets.forEach { it.updateAll(applicationContext) }
            }
        }
    }
}
