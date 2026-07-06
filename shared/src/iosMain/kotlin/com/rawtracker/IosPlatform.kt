package com.rawtracker

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.rawtracker.data.Connectivity
import com.rawtracker.data.FileStore
import com.rawtracker.data.HealthMeal
import com.rawtracker.data.HealthSync
import com.rawtracker.data.HealthSyncResult
import com.rawtracker.data.HealthWater
import com.rawtracker.data.SqlDriverFactory
import com.rawtracker.db.RawTrackerDb
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSPredicate
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.HealthKit.HKAuthorizationStatusSharingAuthorized
import platform.HealthKit.HKHealthStore
import platform.HealthKit.HKObjectType
import platform.HealthKit.HKQuery
import platform.HealthKit.HKQueryOptionStrictStartDate
import platform.HealthKit.predicateForSamplesWithStartDate
import platform.HealthKit.HKQuantity
import platform.HealthKit.HKQuantitySample
import platform.HealthKit.HKQuantityType
import platform.HealthKit.HKQuantityTypeIdentifierDietaryCarbohydrates
import platform.HealthKit.HKQuantityTypeIdentifierDietaryEnergyConsumed
import platform.HealthKit.HKQuantityTypeIdentifierDietaryFatTotal
import platform.HealthKit.HKQuantityTypeIdentifierDietaryProtein
import platform.HealthKit.HKQuantityTypeIdentifierDietaryWater
import platform.HealthKit.HKSampleType
import platform.HealthKit.HKUnit
import platform.posix.memcpy
import kotlin.coroutines.resume

class IosSqlDriverFactory : SqlDriverFactory {
    override fun create(): SqlDriver =
        NativeSqliteDriver(RawTrackerDb.Schema, "rawtracker.db")
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosFileStore : FileStore {
    private fun writeBytesToFile(bytes: ByteArray, path: String) {
        if (bytes.isEmpty()) {
            NSData().writeToFile(path, true)
            return
        }
        bytes.usePinned { pinned ->
            val data = NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
            data.writeToFile(path, true)
        }
    }

    private fun documentsDir(): String =
        (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            .first() as String)

    private fun ensureDir(path: String) {
        NSFileManager.defaultManager.createDirectoryAtPath(path, true, null, null)
    }

    override fun saveImage(bytes: ByteArray): String {
        val dir = documentsDir() + "/images"
        ensureDir(dir)
        val path = "$dir/img_${(NSDate().timeIntervalSince1970 * 1000).toLong()}.jpg"
        writeBytesToFile(bytes, path)
        return path
    }

    override fun readImage(path: String): ByteArray? {
        val data = NSData.dataWithContentsOfFile(path) ?: return null
        val length = data.length.toInt()
        if (length == 0) return ByteArray(0)
        val result = ByteArray(length)
        result.usePinned { pinned ->
            memcpy(pinned.addressOf(0), data.bytes, length.toULong())
        }
        return result
    }

    override fun exportCsv(fileName: String, content: String): String {
        val dir = documentsDir() + "/exports"
        ensureDir(dir)
        val path = "$dir/$fileName"
        writeBytesToFile(content.encodeToByteArray(), path)
        return path
    }
}

class IosConnectivity : Connectivity {
    // Optimistic: assume online. Failures surface inline and queue; the queue
    // is drained on app launch (controller init) and on the next successful send.
    override fun isOnline(): Boolean = true

    override fun registerOnlineListener(onOnline: () -> Unit) {
        // No reachability observer; optimistic model drains on launch/retry instead.
    }
}

/**
 * Write-only HealthKit bridge. All calls degrade to a graceful no-op when HealthKit
 * is unavailable or sharing was not authorized (e.g. running without the entitlement).
 */
@OptIn(ExperimentalForeignApi::class)
class IosHealthSync : HealthSync {
    private val store = HKHealthStore()

    private val energyType get() = HKObjectType.quantityTypeForIdentifier(HKQuantityTypeIdentifierDietaryEnergyConsumed)
    private val proteinType get() = HKObjectType.quantityTypeForIdentifier(HKQuantityTypeIdentifierDietaryProtein)
    private val carbsType get() = HKObjectType.quantityTypeForIdentifier(HKQuantityTypeIdentifierDietaryCarbohydrates)
    private val fatType get() = HKObjectType.quantityTypeForIdentifier(HKQuantityTypeIdentifierDietaryFatTotal)
    private val waterType get() = HKObjectType.quantityTypeForIdentifier(HKQuantityTypeIdentifierDietaryWater)

    private fun shareTypes(): Set<HKSampleType> =
        listOfNotNull(energyType, proteinType, carbsType, fatType, waterType).toSet()

    override suspend fun hasPermissions(): Boolean {
        if (!HKHealthStore.isHealthDataAvailable()) return false
        val energy = energyType ?: return false
        return store.authorizationStatusForType(energy) == HKAuthorizationStatusSharingAuthorized
    }

    override suspend fun requestPermissions(): Boolean {
        if (!HKHealthStore.isHealthDataAvailable()) return false
        val completed = suspendCancellableCoroutine { cont ->
            store.requestAuthorizationToShareTypes(shareTypes(), null) { success, _ ->
                if (cont.isActive) cont.resume(success)
            }
        }
        return completed && hasPermissions()
    }

    override suspend fun reconcileDay(
        dayStartMillis: Long,
        dayEndMillis: Long,
        meals: List<HealthMeal>,
        waters: List<HealthWater>
    ): HealthSyncResult {
        if (!HKHealthStore.isHealthDataAvailable()) return HealthSyncResult.Unavailable
        if (!hasPermissions()) return HealthSyncResult.MissingPermissions
        val startDate = NSDate.dateWithTimeIntervalSince1970(dayStartMillis / 1000.0)
        val endDate = NSDate.dateWithTimeIntervalSince1970(dayEndMillis / 1000.0)
        val predicate = HKQuery.Companion.predicateForSamplesWithStartDate(
            startDate, endDate, HKQueryOptionStrictStartDate
        )
        // HealthKit only deletes samples this app saved, so this clears just our own contribution.
        val deleted = listOfNotNull(energyType, proteinType, carbsType, fatType, waterType)
            .all { type -> deleteSamples(type, predicate) }
        if (!deleted) return HealthSyncResult.failed("delete")

        val kcal = HKUnit.unitFromString("kcal")
        val gram = HKUnit.unitFromString("g")
        val ml = HKUnit.unitFromString("mL")
        val samples = buildList {
            meals.forEach { m ->
                val date = NSDate.dateWithTimeIntervalSince1970(m.atEpochMillis / 1000.0)
                energyType?.let { add(HKQuantitySample.quantitySampleWithType(it, HKQuantity.quantityWithUnit(kcal, m.calories.toDouble()), date, date)) }
                proteinType?.let { add(HKQuantitySample.quantitySampleWithType(it, HKQuantity.quantityWithUnit(gram, m.proteinG.toDouble()), date, date)) }
                carbsType?.let { add(HKQuantitySample.quantitySampleWithType(it, HKQuantity.quantityWithUnit(gram, m.carbsG.toDouble()), date, date)) }
                fatType?.let { add(HKQuantitySample.quantitySampleWithType(it, HKQuantity.quantityWithUnit(gram, m.fatG.toDouble()), date, date)) }
            }
            waters.forEach { w ->
                val date = NSDate.dateWithTimeIntervalSince1970(w.atEpochMillis / 1000.0)
                waterType?.let { add(HKQuantitySample.quantitySampleWithType(it, HKQuantity.quantityWithUnit(ml, w.milliliters.toDouble()), date, date)) }
            }
        }
        return if (save(samples)) HealthSyncResult.Synced else HealthSyncResult.failed("save")
    }

    private suspend fun deleteSamples(type: HKSampleType, predicate: NSPredicate): Boolean {
        return suspendCancellableCoroutine { cont ->
            store.deleteObjectsOfType(type, predicate) { success, _, _ -> if (cont.isActive) cont.resume(success) }
        }
    }

    private suspend fun save(samples: List<HKQuantitySample>): Boolean {
        if (samples.isEmpty()) return true
        return suspendCancellableCoroutine { cont ->
            store.saveObjects(samples) { success, _ -> if (cont.isActive) cont.resume(success) }
        }
    }
}

const val RAWTRACKER_APP_GROUP = "group.com.rawtracker.app"

@OptIn(DelicateCoroutinesApi::class)
private fun writeWidgetData(container: AppContainer) {
    GlobalScope.launch {
        val totals = container.repository.todayTotalsOnce()
        val goals = container.repository.goalsOnce()
        val widgetPrefs = container.repository.widgetPrefsOnce()
        val defaults = NSUserDefaults(suiteName = RAWTRACKER_APP_GROUP)
        defaults.setInteger(totals.calories.toLong(), forKey = "cal")
        defaults.setInteger(totals.protein.toLong(), forKey = "protein")
        defaults.setInteger(totals.carbs.toLong(), forKey = "carbs")
        defaults.setInteger(totals.fat.toLong(), forKey = "fat")
        defaults.setInteger(goals.calories.toLong(), forKey = "goalCal")
        defaults.setInteger(goals.protein.toLong(), forKey = "goalProtein")
        defaults.setInteger(goals.carbs.toLong(), forKey = "goalCarbs")
        defaults.setInteger(goals.fat.toLong(), forKey = "goalFat")
        defaults.setBool(widgetPrefs.showMacros, forKey = "showMacros")
        defaults.setBool(widgetPrefs.showGoal, forKey = "showGoal")
        defaults.setBool(widgetPrefs.showWater, forKey = "showWater")
        defaults.setBool(widgetPrefs.showFood, forKey = "showFood")
        defaults.synchronize()
    }
}

fun createIosContainer(): AppContainer {
    val bundle = NSBundle.mainBundle
    val shortVersion = bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
    val build = bundle.objectForInfoDictionaryKey("CFBundleVersion") as? String
    val version = listOfNotNull(shortVersion, build?.let { "($it)" }).joinToString(" ").ifBlank { "dev" }
    val container = AppContainer(
        driverFactory = IosSqlDriverFactory(),
        fileStore = IosFileStore(),
        healthSync = IosHealthSync(),
        connectivity = IosConnectivity(),
        appVersion = version
    )
    container.onDataChanged = { writeWidgetData(container) }
    writeWidgetData(container)
    return container
}
