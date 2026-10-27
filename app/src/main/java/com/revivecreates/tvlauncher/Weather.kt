package com.revivecreates.tvlauncher

import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt
import org.json.JSONObject

/** Live weather from Open-Meteo: free, no account, no key. */
object Weather {

    data class Now(val temp: Int, val high: Int, val low: Int, val code: Int) {
        val condition: String get() = describe(code)
    }

    private const val URL_STRING =
        "https://api.open-meteo.com/v1/forecast?latitude=${PrayerTimes.LAT}&longitude=${PrayerTimes.LNG}" +
            "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min" +
            "&timezone=Africa%2FCairo&forecast_days=1"

    /** Call off the main thread. Returns null when offline. */
    fun fetch(): Now? = runCatching {
        val conn = (URL(URL_STRING).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
        }
        try {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val current = json.getJSONObject("current")
            val daily = json.getJSONObject("daily")
            Now(
                temp = current.getDouble("temperature_2m").roundToInt(),
                high = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
                low = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
                code = current.getInt("weather_code"),
            )
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67, in 80..82 -> "Rain"
        in 71..77, 85, 86 -> "Snow"
        in 95..99 -> "Thunderstorm"
        else -> "Mild"
    }

    fun isCloudy(code: Int) = code >= 2
}
