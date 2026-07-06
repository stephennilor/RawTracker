package com.rawtracker

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Volume
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.rawtracker.data.Connectivity
import com.rawtracker.data.FileStore
import com.rawtracker.data.HealthMeal
import com.rawtracker.data.HealthSync
import com.rawtracker.data.HealthSyncResult
import com.rawtracker.data.HealthWater
import com.rawtracker.data.SqlDriverFactory
import com.rawtracker.db.RawTrackerDb
import java.io.File
import java.time.Instant
import java.time.ZoneId

class AndroidSqlDriverFactory(private val context: Context) : SqlDriverFactory {
    override fun create(): SqlDriver =
        AndroidSqliteDriver(RawTrackerDb.Schema, context, "rawtracker.db")
}

class AndroidFileStore(private val context: Context) : FileStore {
    override fun saveImage(bytes: ByteArray): String {
        val dir = File(context.filesDir, "images").apply { mkdirs() }
        val file = File(dir, "img_${System.currentTimeMillis()}.jpg")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    override fun readImage(path: String): ByteArray? =
        File(path).takeIf { it.exists() }?.readBytes()

    override fun exportCsv(fileName: String, content: String): String {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(content)
        return file.absolutePath
    }
}

class AndroidConnectivity(private val context: Context) : Connectivity {
    private val cm get() =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    override fun isOnline(): Boolean {
        val manager = cm ?: return true
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun registerOnlineListener(onOnline: () -> Unit) {
        val manager = cm ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        manager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onOnline()
        })
    }
}

/**
 * Write-only Health Connect bridge. Permission UI must be driven by an Activity,
 * so [permissionLauncher] is injected by [MainActivity]; everything degrades to a
 * graceful no-op when Health Connect is unavailable or permissions are missing.
 */
class AndroidHealthSync(private val context: Context) : HealthSync {
    val permissions: Set<String> = setOf(
        HealthPermission.getWritePermission(NutritionRecord::class),
        HealthPermission.getWritePermission(HydrationRecord::class)
    )

    /** Set by the Activity. Launches the system grant UI and resolves with granted perms. */
    var permissionLauncher: (suspend (Set<String>) -> Set<String>)? = null

    private val available: Boolean
        get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private val client: HealthConnectClient? by lazy {
        if (available) runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull() else null
    }

    override suspend fun hasPermissions(): Boolean {
        val c = client ?: return false
        return runCatching {
            c.permissionController.getGrantedPermissions().containsAll(permissions)
        }.getOrDefault(false)
    }

    override suspend fun requestPermissions(): Boolean {
        if (client == null) return false
        if (hasPermissions()) return true
        val launcher = permissionLauncher ?: return false
        return runCatching { launcher(permissions).containsAll(permissions) }.getOrDefault(false)
    }

    override suspend fun reconcileDay(
        dayStartMillis: Long,
        dayEndMillis: Long,
        meals: List<HealthMeal>,
        waters: List<HealthWater>
    ): HealthSyncResult {
        val c = client ?: return HealthSyncResult.Unavailable
        if (!hasPermissions()) return HealthSyncResult.MissingPermissions
        val range = TimeRangeFilter.between(
            Instant.ofEpochMilli(dayStartMillis),
            Instant.ofEpochMilli(dayEndMillis)
        )
        // Health Connect only lets an app delete records it authored, so this wipes exactly our
        // own contribution for the day (including legacy records written before client ids).
        val deletedNutrition = runCatching { c.deleteRecords(NutritionRecord::class, range) }.isSuccess
        val deletedHydration = runCatching { c.deleteRecords(HydrationRecord::class, range) }.isSuccess
        if (!deletedNutrition || !deletedHydration) return HealthSyncResult.failed("delete")
        val records = buildList<Record> {
            meals.forEach { add(nutritionRecord(it)) }
            waters.forEach { add(hydrationRecord(it)) }
        }
        if (records.isNotEmpty()) {
            runCatching { c.insertRecords(records) }
                .getOrElse { return HealthSyncResult.failed("insert") }
        }
        return HealthSyncResult.Synced
    }

    private fun nutritionRecord(m: HealthMeal): NutritionRecord {
        val start = Instant.ofEpochMilli(m.atEpochMillis)
        val end = Instant.ofEpochMilli(m.endEpochMillis ?: (m.atEpochMillis + 1_000))
        val offset = ZoneId.systemDefault().rules.getOffset(start)
        val endOffset = ZoneId.systemDefault().rules.getOffset(end)
        return NutritionRecord(
            startTime = start,
            startZoneOffset = offset,
            endTime = end,
            endZoneOffset = endOffset,
            metadata = Metadata.manualEntry(clientRecordId = m.clientId),
            name = m.foodName,
            energy = Energy.kilocalories(m.calories.toDouble()),
            protein = Mass.grams(m.proteinG.toDouble()),
            totalCarbohydrate = Mass.grams(m.carbsG.toDouble()),
            totalFat = Mass.grams(m.fatG.toDouble())
        )
    }

    private fun hydrationRecord(w: HealthWater): HydrationRecord {
        val start = Instant.ofEpochMilli(w.atEpochMillis)
        val end = Instant.ofEpochMilli(w.endEpochMillis ?: (w.atEpochMillis + 1_000))
        val offset = ZoneId.systemDefault().rules.getOffset(start)
        val endOffset = ZoneId.systemDefault().rules.getOffset(end)
        return HydrationRecord(
            startTime = start,
            startZoneOffset = offset,
            endTime = end,
            endZoneOffset = endOffset,
            metadata = Metadata.manualEntry(clientRecordId = w.clientId),
            volume = Volume.milliliters(w.milliliters.toDouble())
        )
    }
}

fun createAndroidContainer(
    context: Context,
    healthSync: HealthSync = AndroidHealthSync(context.applicationContext),
    appVersion: String = "dev",
    onDataChanged: () -> Unit = {}
): AppContainer {
    val app = context.applicationContext
    return AppContainer(
        driverFactory = AndroidSqlDriverFactory(app),
        fileStore = AndroidFileStore(app),
        healthSync = healthSync,
        connectivity = AndroidConnectivity(app),
        appVersion = appVersion,
        onDataChanged = onDataChanged
    )
}
