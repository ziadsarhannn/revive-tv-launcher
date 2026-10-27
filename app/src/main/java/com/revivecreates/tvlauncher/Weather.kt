package com.revivecreates.tvlauncher

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt
import org.json.JSONObject

/**
 * Live weather for Sheraton Heliopolis. Three free services, no keys, tried in order:
 * Open-Meteo, then MET Norway, then wttr.in. If one is down or blocked, the next one answers.
 */
object Weather {

    private const val TAG = "ReviveWeather"
    private const val UA = "ReviveTV/1.0 (github.com/ziadsarhannn/revive-tv-launcher)"

    data class Now(val temp: Int, val high: Int, val low: Int, val code: Int) {
        val condition: String get() = describe(code)
    }

    /** Last problem, shown in Control Center when there is no weather yet. */
    @Volatile var lastError: String? = null
        private set

    /** Call off the main thread. Returns null only when every service failed. */
    fun fetch(): Now? {
        val errors = ArrayList<String>()
        for ((name, source) in listOf<Pair<String, () -> Now>>(
            "Open-Meteo" to { openMeteo() },
            "MET Norway" to { metNorway() },
            "wttr.in" to { wttr() },
        )) {
            try {
                val now = source()
                lastError = null
                return now
            } catch (e: Exception) {
                val msg = "$name: ${e.javaClass.simpleName} ${e.message.orEmpty()}".trim()
                Log.w(TAG, msg)
                errors += msg
            }
        }
        lastError = errors.firstOrNull()
        return null
    }

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun openMeteo(): Now {
        val json = JSONObject(
            get(
                "https://api.open-meteo.com/v1/forecast?latitude=${PrayerTimes.LAT}&longitude=${PrayerTimes.LNG}" +
                    "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min" +
                    "&timezone=Africa%2FCairo&forecast_days=1"
            )
        )
        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        return Now(
            temp = current.getDouble("temperature_2m").roundToInt(),
            high = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
            low = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
            code = current.getInt("weather_code"),
        )
    }

    private fun metNorway(): Now {
        val json = JSONObject(
            get("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=${PrayerTimes.LAT}&lon=${PrayerTimes.LNG}")
        )
        val series = json.getJSONObject("properties").getJSONArray("timeseries")
        val first = series.getJSONObject(0).getJSONObject("data")
        val temp = first.getJSONObject("instant").getJSONObject("details").getDouble("air_temperature")
        var hi = temp
        var lo = temp
        for (i in 0 until minOf(24, series.length())) {
            val t = series.getJSONObject(i).getJSONObject("data").getJSONObject("instant")
                .getJSONObject("details").getDouble("air_temperature")
            if (t > hi) hi = t
            if (t < lo) lo = t
        }
        val symbol = first.optJSONObject("next_1_hours")?.optJSONObject("summary")?.optString("symbol_code").orEmpty()
        return Now(temp.roundToInt(), hi.roundToInt(), lo.roundToInt(), symbolToCode(symbol))
    }

    private fun wttr(): Now {
        val json = JSONObject(get("https://wttr.in/${PrayerTimes.LAT},${PrayerTimes.LNG}?format=j1"))
        val cur = json.getJSONArray("current_condition").getJSONObject(0)
        val day = json.getJSONArray("weather").getJSONObject(0)
        val desc = cur.optJSONArray("weatherDesc")?.optJSONObject(0)?.optString("value").orEmpty().lowercase()
        return Now(
            temp = cur.getString("temp_C").toDouble().roundToInt(),
            high = day.getString("maxtempC").toDouble().roundToInt(),
            low = day.getString("mintempC").toDouble().roundToInt(),
            code = textToCode(desc),
        )
    }

    private fun symbolToCode(s: String): Int = when {
        s.startsWith("clearsky") -> 0
        s.startsWith("fair") -> 1
        s.startsWith("partlycloudy") -> 2
        s.startsWith("cloudy") -> 3
        s.startsWith("fog") -> 45
        s.contains("thunder") -> 95
        s.contains("snow") || s.contains("sleet") -> 71
        s.contains("rain") -> 61
        else -> 1
    }

    private fun textToCode(s: String): Int = when {
        s.contains("thunder") -> 95
        s.contains("snow") -> 71
        s.contains("rain") || s.contains("shower") -> 61
        s.contains("drizzle") -> 51
        s.contains("fog") || s.contains("mist") || s.contains("haze") -> 45
        s.contains("overcast") -> 3
        s.contains("cloud") -> 2
        s.contains("sunny") || s.contains("clear") -> 0
        else -> 1
    }

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
