package com.geison.phonereminder.diagnostics

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.geison.phonereminder.diagnostics.DiagnosticBreadcrumb.sanitizeValue
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.CustomKeysAndValues
import com.google.firebase.crashlytics.FirebaseCrashlytics

object Diagnostics {
    private const val TAG = "PhoneReminder"
    private var isEnabled = false

    fun initialize(context: Context) {
        isEnabled = FirebaseApp.getApps(context).isNotEmpty()
        val packageInfo = context.packageInfo()
        val readiness = DeliveryReadiness.capture(context)
        if (isEnabled) {
            runCrashlytics { crashlytics ->
                crashlytics.setCustomKeys(
                    CustomKeysAndValues.Builder()
                        .putString("package_name", context.packageName)
                        .putString("app_version_name", packageInfo.versionName ?: "unknown")
                        .putLong("app_version_code", packageInfo.longVersionCodeCompat())
                        .putString("android_sdk", Build.VERSION.SDK_INT.toString())
                        .putBoolean("notifications_enabled", readiness.notificationsEnabled)
                        .putBoolean("post_notifications_granted", readiness.postNotificationsGranted)
                        .putBoolean("reminder_channel_blocked", readiness.reminderChannelBlocked)
                        .putString("reminder_channel_importance", readiness.reminderChannelImportance)
                        .putBoolean("ignoring_battery_optimizations", readiness.ignoringBatteryOptimizations)
                        .putBoolean("power_save_mode", readiness.powerSaveMode)
                        .putBoolean("device_idle_mode", readiness.deviceIdleMode)
                        .build(),
                )
            }
        }
        event(
            "app_start",
            linkedMapOf(
                "version" to (packageInfo.versionName ?: "unknown"),
                "sdk" to Build.VERSION.SDK_INT.toString(),
            ) + readiness.attributes(),
        )
    }

    internal fun refreshDelivery(context: Context): DeliveryReadiness {
        val readiness = DeliveryReadiness.capture(context)
        setKey("notifications_enabled", readiness.notificationsEnabled)
        setKey("post_notifications_granted", readiness.postNotificationsGranted)
        setKey("reminder_channel_blocked", readiness.reminderChannelBlocked)
        setKey("reminder_channel_importance", readiness.reminderChannelImportance)
        setKey("ignoring_battery_optimizations", readiness.ignoringBatteryOptimizations)
        setKey("power_save_mode", readiness.powerSaveMode)
        setKey("device_idle_mode", readiness.deviceIdleMode)
        return readiness
    }

    fun event(
        name: String,
        attributes: Map<String, String> = emptyMap(),
    ) {
        log(DiagnosticBreadcrumb.format(name, attributes))
    }

    fun log(message: String) {
        Log.i(TAG, message)
        runCrashlytics { crashlytics ->
            crashlytics.log(message)
        }
    }

    fun setKey(
        key: String,
        value: Boolean,
    ) {
        runCrashlytics { crashlytics ->
            crashlytics.setCustomKey(key, value)
        }
    }

    fun setKey(
        key: String,
        value: Int,
    ) {
        runCrashlytics { crashlytics ->
            crashlytics.setCustomKey(key, value)
        }
    }

    fun setKey(
        key: String,
        value: String,
    ) {
        runCrashlytics { crashlytics ->
            crashlytics.setCustomKey(key, value.sanitizeValue())
        }
    }

    fun recordNonFatal(
        area: String,
        throwable: Throwable,
        keys: Map<String, String> = emptyMap(),
    ) {
        val exceptionChain = DiagnosticBreadcrumb.exceptionChain(throwable)
        val breadcrumb = DiagnosticBreadcrumb.format(
            name = "non_fatal",
            attributes = linkedMapOf(
                "area" to area,
                "exception" to exceptionChain,
            ) + keys,
        )
        Log.w(TAG, breadcrumb)
        runCrashlytics { crashlytics ->
            crashlytics.log(breadcrumb)
            val builder = CustomKeysAndValues.Builder()
                .putString("diagnostic_area", area.sanitizeValue())
                .putString("diagnostic_exception", exceptionChain)

            keys.forEach { (key, value) ->
                builder.putString(
                    "diagnostic_${DiagnosticBreadcrumb.sanitizeToken(key)}",
                    value.sanitizeValue(),
                )
            }

            crashlytics.recordException(
                DiagnosticBreadcrumb.redactedFailure(area, throwable),
                builder.build(),
            )
        }
    }

    private fun runCrashlytics(block: (FirebaseCrashlytics) -> Unit) {
        if (!isEnabled) {
            return
        }
        runCatching {
            block(FirebaseCrashlytics.getInstance())
        }
    }

    private fun Context.packageInfo(): PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
    }

    private fun PackageInfo.longVersionCodeCompat(): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            @Suppress("DEPRECATION")
            versionCode.toLong()
        }
    }
}
