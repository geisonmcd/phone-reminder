package com.geison.phonereminder.diagnostics

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.CustomKeysAndValues
import com.google.firebase.crashlytics.FirebaseCrashlytics

object Diagnostics {
    private const val MAX_VALUE_LENGTH = 120
    private var isEnabled = false

    fun initialize(context: Context) {
        isEnabled = FirebaseApp.getApps(context).isNotEmpty()
        if (!isEnabled) {
            return
        }

        val packageInfo = context.packageInfo()
        runCrashlytics { crashlytics ->
            crashlytics.setCustomKeys(
                CustomKeysAndValues.Builder()
                    .putString("package_name", context.packageName)
                    .putString("app_version_name", packageInfo.versionName ?: "unknown")
                    .putLong("app_version_code", packageInfo.longVersionCodeCompat())
                    .putString("android_sdk", Build.VERSION.SDK_INT.toString())
                    .putBoolean("notifications_enabled", NotificationManagerCompat.from(context).areNotificationsEnabled())
                    .build(),
            )
        }
        log("app_start")
    }

    fun log(message: String) {
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
            crashlytics.setCustomKey(key, value.safeValue())
        }
    }

    fun recordNonFatal(
        area: String,
        throwable: Throwable,
        keys: Map<String, String> = emptyMap(),
    ) {
        runCrashlytics { crashlytics ->
            val builder = CustomKeysAndValues.Builder()
                .putString("diagnostic_area", area.safeValue())

            keys.forEach { (key, value) ->
                builder.putString("diagnostic_$key", value.safeValue())
            }

            crashlytics.recordException(throwable, builder.build())
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

    private fun String.safeValue(): String {
        return take(MAX_VALUE_LENGTH)
    }
}
