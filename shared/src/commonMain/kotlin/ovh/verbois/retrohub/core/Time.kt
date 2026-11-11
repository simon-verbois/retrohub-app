package ovh.verbois.retrohub.core

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Horloge injectable : les tests figent le temps, l’application lit l’heure système. */
fun interface AppClock {
    fun now(): Instant

    companion object {
        val System = AppClock { Clock.System.now() }
    }
}

private val MONTHS = listOf("janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc.")

fun parseInstant(value: String?): Instant? = value?.takeIf { it.isNotBlank() }?.let {
    // Le serveur écrit parfois des dates UTC sans fuseau (« 2026-11-10T09:12:00 »).
    runCatching { Instant.parse(it) }.getOrElse { _ -> runCatching { Instant.parse(it + "Z") }.getOrNull() }
}

/** « 8 nov. à 21:14 », ou « hier à 21:14 », dans le fuseau de l’appareil. */
fun formatDate(instant: Instant, now: Instant = Clock.System.now(), zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val local = instant.toLocalDateTime(zone)
    val today = now.toLocalDateTime(zone).date
    val time = "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    val day =
        when (today.toEpochDays() - local.date.toEpochDays()) {
            0L -> "aujourd’hui"

            1L -> "hier"

            else ->
                buildString {
                    append(local.date.day).append(' ').append(MONTHS[local.date.month.ordinal])
                    if (local.date.year != today.year) append(' ').append(local.date.year)
                }
        }
    return "$day à $time"
}

/** « Jouée aujourd’hui à 21:14 », « Jouée le 8 nov. à 21:14 ». */
fun formatPlayedAt(instant: Instant, now: Instant = Clock.System.now(), zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val date = formatDate(instant, now, zone)
    return if (date.first().isDigit()) "Jouée le $date" else "Jouée $date"
}

/** « À l’instant », « il y a 5 min », « il y a 3 h », puis la date. */
fun formatRelative(instant: Instant, now: Instant = Clock.System.now(), zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val elapsed = now - instant
    return when {
        elapsed < 60.seconds -> "À l’instant"
        elapsed < Duration.parse("1h") -> "il y a ${elapsed.inWholeMinutes} min"
        elapsed < Duration.parse("20h") -> "il y a ${elapsed.inWholeHours} h"
        else -> formatDate(instant, now, zone).replaceFirstChar { it.uppercase() }
    }
}

/** « 3 h 20 min », « 45 min », « moins d’une minute ». */
fun formatPlayTime(seconds: Long): String {
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        hours > 0 && minutes % 60 > 0 -> "$hours h ${minutes % 60} min"
        hours > 0 -> "$hours h"
        minutes > 0 -> "$minutes min"
        else -> "moins d’une minute"
    }
}

fun formatBytes(bytes: Long): String {
    val units = listOf("o", "Ko", "Mo", "Go", "To")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    val rounded = if (value >= 100 || unit == 0) value.toLong().toString() else ((value * 10).toLong() / 10.0).toString()
    return "${rounded.replace('.', ',')} ${units[unit]}"
}
