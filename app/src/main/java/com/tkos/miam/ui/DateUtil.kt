package com.tkos.miam.ui

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object DateUtil {
    fun today(): LocalDate = LocalDate.now()

    fun startOfWeek(date: LocalDate = today()): LocalDate = date.with(DayOfWeek.MONDAY)

    /** Fenêtre de la liste de courses : de ce lundi à 4 semaines plus tard. */
    fun shoppingWindow(): Pair<Long, Long> {
        val start = startOfWeek()
        return start.toEpochDay() to start.plusDays(27).toEpochDay()
    }

    fun weekWindow(weekStart: LocalDate): Pair<Long, Long> = weekStart.toEpochDay() to weekStart.plusDays(6).toEpochDay()

    private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)
    private val shortFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)

    fun dayLabel(date: LocalDate): String = dayFmt.format(date).replaceFirstChar { it.titlecase(Locale.FRENCH) }
    fun shortLabel(date: LocalDate): String = shortFmt.format(date)
    fun dayShort(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.FRENCH).replaceFirstChar { it.titlecase(Locale.FRENCH) }.trimEnd('.')

    fun weekLabel(weekStart: LocalDate): String {
        val end = weekStart.plusDays(6)
        return "${shortLabel(weekStart)} – ${shortLabel(end)}"
    }

    fun relativeDay(date: LocalDate): String = when (date) {
        today() -> "Aujourd'hui"
        today().plusDays(1) -> "Demain"
        today().minusDays(1) -> "Hier"
        else -> dayLabel(date)
    }
}
