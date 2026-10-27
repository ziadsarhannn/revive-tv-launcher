package com.revivecreates.tvlauncher

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import org.json.JSONObject

/**
 * The "What's New" list for the Top Shelf slideshow.
 * It lives in the repo as feed/whats-new.json and is refreshed every week.
 * The launcher keeps the last copy it downloaded, so the slideshow still works offline.
 */
object WhatsNew {

    private const val FEED_URL =
        "https://raw.githubusercontent.com/ziadsarhannn/revive-tv-launcher/main/feed/whats-new.json"

    data class Item(
        val title: String,
        val titleAr: String?,
        val type: String,
        val year: Int?,
        val genre: String?,
        val date: String?,
        val about: String?,
    ) {
        /** "Series · 2026 · Thriller" */
        val meta: String get() = listOfNotNull(type.ifBlank { null }, year?.toString(), genre).joinToString(" · ")
    }

    data class Feed(val apps: List<Pair<List<String>, List<Item>>>) {
        fun forLabel(label: String): List<Item> {
            val l = label.lowercase(Locale.ROOT)
            return apps.firstOrNull { (match, _) -> match.any { l.contains(it) } }?.second.orEmpty()
        }
    }

    fun cached(context: Context): Feed? =
        context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
            .getString("whats_new", null)?.let { parse(it) }

    /** Call off the main thread. Returns null when offline, keeping the cached copy. */
    fun refresh(context: Context): Feed? = runCatching {
        val conn = (URL("$FEED_URL?t=${System.currentTimeMillis() / 3_600_000}").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            useCaches = false
        }
        val body = try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        val feed = parse(body) ?: return@runCatching null
        context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
            .edit().putString("whats_new", body).apply()
        feed
    }.getOrNull()

    private fun parse(body: String): Feed? = runCatching {
        val json = JSONObject(body)
        val apps = json.getJSONArray("apps")
        val out = ArrayList<Pair<List<String>, List<Item>>>()
        for (i in 0 until apps.length()) {
            val app = apps.getJSONObject(i)
            val match = app.optJSONArray("match")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it).lowercase(Locale.ROOT) }
            } ?: listOf(app.optString("name").lowercase(Locale.ROOT))
            val itemsJson = app.optJSONArray("items") ?: continue
            val items = (0 until itemsJson.length()).map { j ->
                val o = itemsJson.getJSONObject(j)
                Item(
                    title = o.optString("title"),
                    titleAr = o.optString("titleAr").takeIf { s -> s.isNotBlank() && s != "null" },
                    type = o.optString("type"),
                    year = if (o.has("year") && !o.isNull("year")) o.optInt("year") else null,
                    genre = o.optString("genre").takeIf { s -> s.isNotBlank() && s != "null" },
                    date = o.optString("date").takeIf { s -> s.isNotBlank() && s != "null" },
                    about = o.optString("about").takeIf { s -> s.isNotBlank() && s != "null" },
                )
            }.filter { it.title.isNotBlank() }
            out += match to items
        }
        Feed(out)
    }.getOrNull()

    /** "New", "Out today", "Coming Fri 30 Oct" or "Coming soon". */
    fun badge(item: Item, nowMillis: Long): String {
        val date = item.date ?: return "Coming soon"
        val day = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date) }.getOrNull() ?: return "New"
        val today = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return when {
            day.time > today -> "Coming " + SimpleDateFormat("EEE d MMM", Locale.US).format(day)
            day.time == today -> "Out today"
            else -> "New"
        }
    }
}
