package com.revivecreates.tvlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.DisplayMetrics
import androidx.core.content.res.ResourcesCompat
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette

/** One app on the home screen. */
data class TvApp(
    val packageName: String,
    val className: String,
    val label: String,
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
    /** Background for tiles that have no TV banner. */
    val tint: Color,
    /** Deep color behind the Top Shelf. */
    val heroColor: Color,
    /** The app's signature color, for the Top Shelf accent bar and badge. */
    val accent: Color,
)

class AppRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private val cache = HashMap<String, TvApp>()

    /** Streaming apps that go in the top row the first time the launcher runs. */
    private val favorites = listOf(
        "netflix", "shahid", "yango", "youtube", "watch it", "watchit",
        "osn", "anghami", "spotify", "disney", "prime video"
    )

    fun loadApps(): List<TvApp> {
        val byPackage = LinkedHashMap<String, ResolveInfo>()
        // TV apps first so their TV entry point wins over a phone one.
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            @Suppress("DEPRECATION")
            val found = pm.queryIntentActivities(intent, 0)
            for (ri in found) {
                val pkg = ri.activityInfo.packageName
                if (pkg == context.packageName) continue
                if (!byPackage.containsKey(pkg)) byPackage[pkg] = ri
            }
        }

        val apps = byPackage.values.mapNotNull { ri ->
            val key = ri.activityInfo.packageName + "/" + ri.activityInfo.name
            cache[key] ?: runCatching { build(ri) }.getOrNull()?.also { cache[key] = it }
        }
        return order(apps)
    }

    private fun build(ri: ResolveInfo): TvApp {
        val ai = ri.activityInfo
        val label = ri.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { ai.packageName }
        // Ask each app for its sharpest artwork (the highest density it ships), not the default size.
        val bannerRes = if (ai.banner != 0) ai.banner else ai.applicationInfo.banner
        val iconRes = if (ri.iconResource != 0) ri.iconResource else if (ai.icon != 0) ai.icon else ai.applicationInfo.icon
        val bannerDrawable: Drawable? = sharpest(ai.applicationInfo, bannerRes)
            ?: ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm)
        val iconDrawable: Drawable? = sharpest(ai.applicationInfo, iconRes)
            ?: runCatching { ri.loadIcon(pm) }.getOrNull()

        val bannerBmp = bannerDrawable?.let { runCatching { it.toBitmap(960, 540, Bitmap.Config.ARGB_8888) }.getOrNull() }
        val iconBmp = iconDrawable?.let { runCatching { it.toBitmap(288, 288, Bitmap.Config.ARGB_8888) }.getOrNull() }

        val source = bannerBmp ?: iconBmp
        var dominant = 0xFF1C1C1F.toInt()
        var deep = 0xFF1C1C1F.toInt()
        var accent = 0xFF3D6AA8.toInt()
        if (source != null) {
            val palette = Palette.from(source).maximumColorCount(16).generate()
            dominant = palette.getDominantColor(dominant)
            deep = palette.getDarkMutedColor(palette.getDarkVibrantColor(darken(dominant, 0.55f)))
            accent = palette.getVibrantColor(palette.getDarkVibrantColor(palette.getLightVibrantColor(dominant)))
            // Keep white badge text readable: tone down very light colors, lift near black ones.
            val lum = ColorUtils.calculateLuminance(accent)
            if (lum > 0.45) accent = darken(accent, 0.45f)
            if (lum < 0.02) accent = 0xFF3D6AA8.toInt()
        }

        return TvApp(
            packageName = ai.packageName,
            className = ai.name,
            label = label,
            banner = bannerBmp?.asImageBitmap(),
            icon = iconBmp?.asImageBitmap(),
            tint = Color(deep),
            heroColor = Color(darken(dominant, 0.62f)),
            accent = Color(accent),
        )
    }

    private fun sharpest(appInfo: ApplicationInfo, resId: Int): Drawable? {
        if (resId == 0) return null
        return runCatching {
            val res = pm.getResourcesForApplication(appInfo)
            ResourcesCompat.getDrawableForDensity(res, resId, DisplayMetrics.DENSITY_XXXHIGH, null)
        }.getOrNull()
    }

    private fun darken(color: Int, amount: Float): Int =
        ColorUtils.blendARGB(color, 0xFF000000.toInt(), amount)

    // ---------- order and history ----------

    private fun order(apps: List<TvApp>): List<TvApp> {
        val saved = prefs.getString("order", null)
            ?.split("|")?.filter { it.isNotEmpty() }
        if (saved != null) {
            val index = saved.withIndex().associate { it.value to it.index }
            val known = apps.filter { index.containsKey(it.packageName) }
                .sortedBy { index[it.packageName] }
            val fresh = apps.filter { !index.containsKey(it.packageName) }
                .sortedBy { it.label.lowercase() }
            return known + fresh
        }
        val fav = apps.mapNotNull { app ->
            val rank = favorites.indexOfFirst { app.label.lowercase().contains(it) }
            if (rank >= 0) rank to app else null
        }.sortedBy { it.first }.map { it.second }
        val rest = apps.filter { it !in fav }.sortedBy { it.label.lowercase() }
        return fav + rest
    }

    fun saveOrder(packages: List<String>) {
        prefs.edit().putString("order", packages.joinToString("|")).apply()
    }

    fun markOpened(pkg: String) {
        prefs.edit().putLong("opened_$pkg", System.currentTimeMillis()).apply()
    }

    fun lastOpened(pkg: String): Long = prefs.getLong("opened_$pkg", 0L)

    fun forget(pkg: String) {
        cache.keys.filter { it.startsWith("$pkg/") }.forEach { cache.remove(it) }
    }
}
