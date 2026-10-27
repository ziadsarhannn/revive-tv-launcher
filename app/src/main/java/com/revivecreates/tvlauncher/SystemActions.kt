package com.revivecreates.tvlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** Everything the launcher asks Android to do. Each call fails quietly. */
object SystemActions {

    private fun start(context: Context, intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }

    fun launchApp(context: Context, app: TvApp): Boolean {
        val direct = Intent(Intent.ACTION_MAIN)
            .setComponent(ComponentName(app.packageName, app.className))
            .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (start(context, direct)) return true
        val pm = context.packageManager
        val fallback = pm.getLeanbackLaunchIntentForPackage(app.packageName)
            ?: pm.getLaunchIntentForPackage(app.packageName)
        return fallback != null && start(context, fallback)
    }

    fun settings(context: Context, action: String): Boolean =
        start(context, Intent(action)) || start(context, Intent(Settings.ACTION_SETTINGS))

    fun screenSaver(context: Context): Boolean {
        val now = Intent(Intent.ACTION_MAIN).setComponent(
            ComponentName("com.android.systemui", "com.android.systemui.Somnambulator")
        )
        return start(context, now) || settings(context, Settings.ACTION_DREAM_SETTINGS)
    }

    fun search(context: Context): Boolean =
        start(context, Intent("android.search.action.GLOBAL_SEARCH")) ||
            start(context, Intent(Intent.ACTION_ASSIST)) ||
            start(context, Intent(Intent.ACTION_WEB_SEARCH))

    /** Opens the box's original home screen, whatever brand it is. */
    fun originalHome(context: Context): Boolean {
        val pm = context.packageManager
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        val others = pm.queryIntentActivities(home, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .filter { it.activityInfo.packageName != "com.android.settings" }
            .sortedByDescending { (it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 }
        for (ri in others) {
            val i = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setComponent(ComponentName(ri.activityInfo.packageName, ri.activityInfo.name))
            if (start(context, i)) return true
        }
        return false
    }

    /** The system screen for choosing which launcher is home, when the box has one. */
    fun chooseHomeApp(context: Context): Boolean =
        start(context, Intent("android.settings.HOME_SETTINGS")) ||
            start(context, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) ||
            settings(context, Settings.ACTION_APPLICATION_SETTINGS)

    fun appInfo(context: Context, pkg: String): Boolean =
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))

    fun uninstall(context: Context, pkg: String): Boolean =
        start(context, Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))

    fun networkLabel(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "Unknown"
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "Offline"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Connected"
            else -> "Online"
        }
    }

    fun deviceName(context: Context): String =
        runCatching { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: Build.MODEL
}
