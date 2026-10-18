package app.meteorite

import app.meteorite.data.Hour
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** How hard it rains, in plain words instead of millimetres. */
enum class Rain(val drops: String) {
    NONE(""), LIGHT("💧"), MODERATE("💧💧"), HEAVY("💧💧💧")
}

fun dayRain(mm: Double) = when {
    mm < 0.5 -> Rain.NONE
    mm < 3 -> Rain.LIGHT
    mm < 10 -> Rain.MODERATE
    else -> Rain.HEAVY
}

fun hourRain(mm: Double) = when {
    mm < 0.1 -> Rain.NONE
    mm < 0.7 -> Rain.LIGHT
    mm < 2.5 -> Rain.MODERATE
    else -> Rain.HEAVY
}

fun rainLabel(rain: Rain) = when (rain) {
    Rain.NONE -> "Pas de pluie"
    Rain.LIGHT -> "Petite pluie"
    Rain.MODERATE -> "Pluie modérée"
    Rain.HEAVY -> "Grosse pluie"
}

/** Snow is measured in centimetres; the same levels are reused for the wording. */
fun daySnow(cm: Double) = when {
    cm < 0.5 -> Rain.NONE
    cm < 2 -> Rain.LIGHT
    cm < 8 -> Rain.MODERATE
    else -> Rain.HEAVY
}

fun hourSnow(cm: Double) = when {
    cm < 0.1 -> Rain.NONE
    cm < 0.5 -> Rain.LIGHT
    cm < 2 -> Rain.MODERATE
    else -> Rain.HEAVY
}

fun snowLabel(level: Rain) = when (level) {
    Rain.NONE -> "Pas de neige"
    Rain.LIGHT -> "Un peu de neige"
    Rain.MODERATE -> "Neige"
    Rain.HEAVY -> "Forte neige"
}

/** "2,5 cm" below 10 cm, whole centimetres above. */
fun formatCm(cm: Double) =
    if (cm < 10) String.format(Locale.FRENCH, "%.1f cm", cm) else "${cm.roundToInt()} cm"

fun windLabel(kmh: Int) = when {
    kmh < 20 -> "Vent faible"
    kmh < 40 -> "Vent modéré"
    kmh < 60 -> "Vent fort"
    else -> "Vent très fort"
}

fun weatherEmoji(code: Int, isDay: Boolean = true) = when (code) {
    0 -> if (isDay) "☀️" else "🌙"
    1 -> if (isDay) "🌤️" else "🌙"
    2 -> "⛅"
    3 -> "☁️"
    45, 48 -> "🌫️"
    51, 53, 55, 80 -> "🌦️"
    56, 57, 61, 63, 65, 66, 67, 81, 82 -> "🌧️"
    71, 73, 75, 77, 85, 86 -> "❄️"
    95, 96, 99 -> "⛈️"
    else -> "☁️"
}

fun weatherLabel(code: Int) = when (code) {
    0 -> "Ciel dégagé"
    1 -> "Plutôt dégagé"
    2 -> "Partiellement nuageux"
    3 -> "Couvert"
    45, 48 -> "Brouillard"
    51, 53, 55 -> "Bruine"
    56, 57 -> "Bruine verglaçante"
    61, 63, 65 -> "Pluie"
    66, 67 -> "Pluie verglaçante"
    71, 73, 75, 77 -> "Neige"
    80, 81, 82 -> "Averses"
    85, 86 -> "Averses de neige"
    95 -> "Orage"
    96, 99 -> "Orage et grêle"
    else -> "Nuageux"
}

private val fullDate = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)
private val shortDate = DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH)
private val weekday = DateTimeFormatter.ofPattern("EEEE", Locale.FRENCH)

private fun String.capitalized() = replaceFirstChar { it.uppercase() }

fun dayTitle(date: LocalDate, today: LocalDate) = when (date) {
    today -> "Aujourd'hui"
    else -> date.format(weekday).capitalized()
}

fun daySubtitle(date: LocalDate) = date.format(shortDate)

fun fullDayTitle(date: LocalDate) = date.format(fullDate).capitalized()

/** Rain and snow wording for the hours left today, or a calm sentence when nothing is expected. */
fun restOfDaySentence(hours: List<Hour>, now: LocalDateTime): String {
    val rainHours = hours.filter { hourRain(it.rainMm) != Rain.NONE }
    val snowHours = hours.filter { hourSnow(it.snowCm) != Rain.NONE }
    val parts = listOfNotNull(
        span(rainHours, now) { h -> hourRain(h.rainMm) }
            ?.let { (level, when_) -> "${level.drops} ${rainLabel(level)} $when_" },
        span(snowHours, now) { h -> hourSnow(h.snowCm) }
            ?.let { (level, when_) -> "❄️ ${snowLabel(level)} $when_" },
    )
    return if (parts.isEmpty()) "Pas de pluie d'ici ce soir" else parts.joinToString("\n")
}

/** Worst level and the time span ("de 15 h à 18 h") of [wet] hours, or null when there are none. */
private fun span(wet: List<Hour>, now: LocalDateTime, level: (Hour) -> Rain): Pair<Rain, String>? {
    if (wet.isEmpty()) return null
    val worst = wet.maxOf(level)
    val first = wet.first().time.hour
    val end = wet.last().time.hour + 1
    val endText = if (end == 24) "minuit" else "$end h"
    val text = when {
        first == now.hour -> "jusqu'à $endText"
        first + 1 == end -> "vers $first h"
        else -> "de $first h à $endText"
    }
    return worst to text
}

private val clockTime = DateTimeFormatter.ofPattern("H:mm", Locale.FRENCH)

/** "Mis à jour à 14:32", with the date when it was not today. */
fun updatedText(savedAt: Long): String {
    val time = Instant.ofEpochMilli(savedAt).atZone(ZoneId.systemDefault())
    val at = time.format(clockTime)
    return if (time.toLocalDate() == LocalDate.now()) "Mis à jour à $at"
    else "Mis à jour le ${time.format(shortDate)} à $at"
}
