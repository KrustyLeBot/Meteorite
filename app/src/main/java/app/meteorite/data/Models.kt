package app.meteorite.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

data class City(
    val id: String,
    val name: String,
    val subtitle: String,
    val lat: Double,
    val lon: Double,
) {
    val isGps get() = id == GPS_ID

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
    val code: Int,
    val rainMm: Double,
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
    val windKmh: Int,
    val sunrise: LocalTime,
    val sunset: LocalTime,
    val hours: List<Hour>,
)

data class Forecast(val current: Current, val days: List<Day>) {
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
