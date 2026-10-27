package com.revivecreates.tvlauncher

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Prayer times worked out on the box itself, so they need no internet and no subscription.
 * Uses the Egyptian General Authority of Survey method: Fajr at 19.5 degrees, Isha at 17.5 degrees,
 * Asr with the standard shadow length. Summer time is handled through the Africa/Cairo time zone.
 */
object PrayerTimes {

    const val LAT = 30.10   // Sheraton Heliopolis, Cairo
    const val LNG = 31.37
    val CAIRO: TimeZone = TimeZone.getTimeZone("Africa/Cairo")

    val names = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")

    data class Day(val times: List<Double>) // hours after local midnight, same order as names

    fun forToday(nowMillis: Long = System.currentTimeMillis()): Day {
        val cal = Calendar.getInstance(CAIRO).apply { timeInMillis = nowMillis }
        val tz = CAIRO.getOffset(nowMillis) / 3_600_000.0
        val all = compute(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), LAT, LNG, tz)
        return Day(listOf(all[0], all[2], all[3], all[4], all[5]))
    }

    /** Hours after midnight in Cairo right now. */
    fun cairoHours(nowMillis: Long = System.currentTimeMillis()): Double {
        val cal = Calendar.getInstance(CAIRO).apply { timeInMillis = nowMillis }
        return cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60.0
    }

    fun format12(hours: Double): String {
        val t = ((hours % 24) + 24) % 24
        var h = floor(t).toInt()
        var m = Math.round((t - h) * 60).toInt()
        if (m == 60) { h = (h + 1) % 24; m = 0 }
        val ap = if (h < 12) "AM" else "PM"
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "$h12:${m.toString().padStart(2, '0')} $ap"
    }

    private fun rad(x: Double) = Math.toRadians(x)
    private fun deg(x: Double) = Math.toDegrees(x)
    private fun dsin(x: Double) = sin(rad(x))
    private fun dcos(x: Double) = cos(rad(x))
    private fun dtan(x: Double) = tan(rad(x))
    private fun fix(a: Double, b: Double): Double { val r = a - b * floor(a / b); return if (r < 0) r + b else r }

    /** Returns Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha in local hours. */
    fun compute(year: Int, month: Int, day: Int, lat: Double, lng: Double, tz: Double): List<Double> {
        var y = year
        var mo = month
        if (mo <= 2) { y -= 1; mo += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4)
        val jd = floor(365.25 * (y + 4716)) + floor(30.6001 * (mo + 1)) + day + b - 1524.5 - lng / (15 * 24)

        fun sun(t: Double): Pair<Double, Double> {
            val d = jd + t - 2451545.0
            val g = fix(357.529 + 0.98560028 * d, 360.0)
            val q = fix(280.459 + 0.98564736 * d, 360.0)
            val l = fix(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g), 360.0)
            val e = 23.439 - 0.00000036 * d
            val ra = deg(atan2(dcos(e) * dsin(l), dcos(l))) / 15
            val decl = deg(asin(dsin(e) * dsin(l)))
            val eqt = q / 15 - fix(ra, 24.0)
            return decl to eqt
        }

        fun noon(t: Double) = fix(12 - sun(t).second, 24.0)

        fun angleTime(angle: Double, t: Double, before: Boolean): Double {
            val decl = sun(t).first
            val v = ((-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat))).coerceIn(-1.0, 1.0)
            val x = deg(acos(v)) / 15
            return noon(t) + if (before) -x else x
        }

        fun asr(t: Double): Double {
            val decl = sun(t).first
            val angle = -deg(atan(1 / (1 + dtan(abs(lat - decl)))))
            return angleTime(angle, t, false)
        }

        val adj = tz - lng / 15
        return listOf(
            angleTime(19.5, 5 / 24.0, true),
            angleTime(0.833, 6 / 24.0, true),
            noon(12 / 24.0),
            asr(13 / 24.0),
            angleTime(0.833, 18 / 24.0, false),
            angleTime(17.5, 18 / 24.0, false),
        ).map { it + adj }
    }
}
