package app.meteorite.data

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

data class City(
    val id: String,
    val name: String,
    val subtitle: String,
    val lat: Double,
    val lon: Double,
) {
    val isGps get() = id == GPS_ID

    /** "Ma position · Grenoble" for the phone position, the plain name otherwise. */
    val displayName get() = if (isGps && subtitle.isNotBlank()) "$name · $subtitle" else name

    companion object {
        const val GPS_ID = "gps"
    }
}

data class Current(
    val time: LocalDateTime,
    val temp: Int,
    val feelsLike: Int,
    val code: Int,
    val isDay: Boolean,
    val windKmh: Int,
)

data class Hour(
    val time: LocalDateTime,
    val temp: Int,
    val feelsLike: Int,
    val code: Int,
    val rainMm: Double,
    val snowCm: Double,
    val windKmh: Int,
    val isDay: Boolean,
)

data class Day(
    val date: LocalDate,
    val code: Int,
    val tMax: Int,
    val tMin: Int,
    val rainMm: Double,
    val rainHours: Int,
    val snowCm: Double,
    val windKmh: Int,
    val sunrise: LocalTime,
    val sunset: LocalTime,
    val hours: List<Hour>,
)

/** A forecast kept on the phone, with the time it was downloaded (epoch millis). */
data class Cached(val forecast: Forecast, val savedAt: Long)

data class Forecast(val current: Current, val days: List<Day>, val utcOffsetSeconds: Int) {
    /**
     * Brings a possibly old forecast up to date with the phone clock: days that are over are
     * dropped, and "now" comes from the hourly data when the current conditions are too old.
     */
    fun rolled(): Forecast {
        val now = LocalDateTime.now(ZoneOffset.ofTotalSeconds(utcOffsetSeconds))
        val remainingDays = days.filter { !it.date.isBefore(now.toLocalDate()) }
        if (remainingDays.isEmpty()) return this
        val hour = now.truncatedTo(ChronoUnit.HOURS)
        val fresh = Duration.between(current.time, now).abs() < Duration.ofMinutes(45)
        val cur = if (fresh) current else remainingDays.first().hours.firstOrNull { it.time == hour }
            ?.let { Current(hour, it.temp, it.feelsLike, it.code, it.isDay, it.windKmh) }
            ?: current
        return Forecast(cur, remainingDays, utcOffsetSeconds)
    }

    /** Hours of today that are not over yet (local time of the city). */
    fun todayRemaining(): List<Hour> {
        val now = current.time.truncatedTo(ChronoUnit.HOURS)
        return days.first().hours.filter { !it.time.isBefore(now) }
    }

    /** The hours after the current one, across days. */
    fun upcoming(): List<Hour> {
        val now = current.time.truncatedTo(ChronoUnit.HOURS)
        return days.flatMap { it.hours }.filter { it.time.isAfter(now) }
    }
}
