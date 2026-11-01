package com.matrix.messenger.ui.components

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime

/**
 * Date-aware time labels replacing the old `formatTime()` (which rendered
 * the time-of-day of the epoch second, ignoring the date entirely).
 */
object TimeFormat {

    private val RU_MONTHS_GENITIVE = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря"
    )

    private val RU_WEEKDAYS = listOf(
        "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"
    )

    private fun toLocalDate(timestamp: Long): LocalDate =
        Instant.fromEpochMilliseconds(timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date

    private fun todayDate(): LocalDate =
        kotlinx.datetime.Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date

    private fun two(v: Int): String = v.toString().padStart(2, '0')

    /** HH:MM for bubble timestamps. */
    fun timeOnly(timestamp: Long): String {
        val dt = Instant.fromEpochMilliseconds(timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        return "${two(dt.hour)}:${two(dt.minute)}"
    }

    /**
     * Room-list row label: today -> HH:MM, yesterday -> "Вчера",
     * within the last 7 days -> weekday ("Понедельник"), else dd.MM.yy.
     */
    fun listLabel(timestamp: Long): String {
        val date = toLocalDate(timestamp)
        val today = todayDate()
        return when (date.daysUntil(today)) {
            0 -> timeOnly(timestamp)
            1 -> "Вчера"
            in 2..6 -> RU_WEEKDAYS[date.dayOfWeek.isoDayNumber - 1]
            else -> "${two(date.dayOfMonth)}.${two(date.monthNumber)}.${two(date.year % 100)}"
        }
    }

    /**
     * Centered chat separator: "Сегодня" / "Вчера" / "12 октября" / "12 октября 2025".
     */
    fun separatorLabel(timestamp: Long): String {
        val date = toLocalDate(timestamp)
        val today = todayDate()
        return when (date.daysUntil(today)) {
            0 -> "Сегодня"
            1 -> "Вчера"
            else -> {
                val base = "${date.dayOfMonth} ${RU_MONTHS_GENITIVE[date.monthNumber - 1]}"
                if (date.year == today.year) base else "$base ${date.year}"
            }
        }
    }

    /** Stable per-calendar-day key for grouping messages under one separator. */
    fun epochDay(timestamp: Long): Long =
        toLocalDate(timestamp).toEpochDays().toLong()
}
