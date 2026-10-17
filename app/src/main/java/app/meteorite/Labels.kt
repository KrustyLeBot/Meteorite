package app.meteorite

import app.meteorite.data.Hour
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How hard it rains, in plain words instead of millimetres. */
enum class Rain(val drops: String) {
    NONE(""), LIGHT("💧"), MODERATE("💧💧"), HEAVY("💧💧💧")
}

fun isSnow(code: Int) = code in 71..77 || code == 85 || code == 86

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

fun rainLabel(rain: Rain, snow: Boolean): String = if (snow) {
    when (rain) {
        Rain.NONE -> "Pas de neige"
        Rain.LIGHT -> "Un peu de neige"
        Rain.MODERATE -> "Neige"
        Rain.HEAVY -> "Forte neige"
    }
} else {
    when (rain) {
        Rain.NONE -> "Pas de pluie"
        Rain.LIGHT -> "Petite pluie"
        Rain.MODERATE -> "Pluie modérée"
        Rain.HEAVY -> "Grosse pluie"
    }
}

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
    today.plusDays(1) -> "Demain"
    else -> date.format(weekday).capitalized()
}

fun daySubtitle(date: LocalDate) = date.format(shortDate)

fun fullDayTitle(date: LocalDate) = date.format(fullDate).capitalized()

/** "Petite pluie de 15 h à 18 h", or a calm sentence when nothing is expected. */
fun restOfDaySentence(hours: List<Hour>, now: LocalDateTime): String {
    val wet = hours.filter { hourRain(it.rainMm) != Rain.NONE }
    if (wet.isEmpty()) return "Pas de pluie d'ici ce soir"
    val worst = wet.maxOf { hourRain(it.rainMm) }
    val label = rainLabel(worst, wet.any { isSnow(it.code) })
    val first = wet.first().time.hour
    val end = wet.last().time.hour + 1
    val endText = if (end == 24) "minuit" else "$end h"
    return when {
        first == now.hour -> "${worst.drops} $label jusqu'à $endText"
        first + 1 == end -> "${worst.drops} $label vers $first h"
        else -> "${worst.drops} $label de $first h à $endText"
    }
}
