package com.matrix.messenger.receiver

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

object OemBatteryHelper {

    private const val TAG = "OemBatteryHelper"

    enum class Oem(val manufacturer: String) {
        XIAOMI("xiaomi"),
        SAMSUNG("samsung"),
        HUAWEI("huawei"),
        OPPO("oppo"),
        ONEPLUS("oneplus"),
        VIVO("vivo"),
        GENERIC("")
    }

    fun detectOem(): Oem {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return Oem.entries.firstOrNull { oem ->
            oem != Oem.GENERIC && manufacturer.contains(oem.manufacturer)
        } ?: Oem.GENERIC
    }

    fun openBatterySettings(context: Context) {
        val oem = detectOem()
        val opened = when (oem) {
            Oem.XIAOMI -> openXiaomiAutostart(context) || openGenericBattery(context)
            Oem.SAMSUNG -> openSamsungBattery(context) || openGenericBattery(context)
            Oem.HUAWEI -> openHuaweiAppLaunch(context) || openGenericBattery(context)
            Oem.OPPO -> openOppoBattery(context) || openGenericBattery(context)
            Oem.ONEPLUS -> openOnePlusBattery(context) || openGenericBattery(context)
            Oem.VIVO -> openVivoBackground(context) || openGenericBattery(context)
            Oem.GENERIC -> openGenericBattery(context)
        }
        if (!opened) {
            openAppSettings(context)
        }
    }

    fun requestBatteryOptimizationBypass(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (pm.isIgnoringBatteryOptimizations(context.packageName)) return true

        return try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Battery optimization request failed", e)
            openGenericBattery(context)
        }
    }

    private fun openXiaomiAutostart(context: Context): Boolean {
        return tryIntent(context, Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun openSamsungBattery(context: Context): Boolean {
        return tryIntent(context, Intent().apply {
            component = ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }) || tryIntent(context, Intent().apply {
            component = ComponentName(
                "com.samsung.android.sm",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun openHuaweiAppLaunch(context: Context): Boolean {
        return tryIntent(context, Intent().apply {
            component = ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun openOppoBattery(context: Context): Boolean {
        return tryIntent(context, Intent().apply {
            component = ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }) || tryIntent(context, Intent().apply {
            component = ComponentName(
                "com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun openOnePlusBattery(context: Context): Boolean {
        return tryIntent(context, Intent().apply {
            component = ComponentName(
                "com.oneplus.security",
                "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun openVivoBackground(context: Context): Boolean {
        return tryIntent(context, Intent().apply {
            component = ComponentName(
                "com.vivo.abe",
                "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }) || tryIntent(context, Intent().apply {
            component = ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun openGenericBattery(context: Context): Boolean {
        return tryIntent(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun openAppSettings(context: Context): Boolean {
        return tryIntent(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun tryIntent(context: Context, intent: Intent): Boolean {
        return try {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.d(TAG, "Intent failed: ${intent.component}", e)
            false
        }
    }
}
