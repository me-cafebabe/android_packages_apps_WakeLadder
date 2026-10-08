package dev.me_cafebabe.wakeladder.domain

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FallbackTest {
    // 2026-10-09 is a Friday.
    private val fridayNight = LocalDateTime.of(2026, 10, 9, 22, 30)
    private val defaults = mapOf(DayOfWeek.SATURDAY to LocalTime.of(8, 0), DayOfWeek.MONDAY to LocalTime.of(6, 30))

    @Test
    fun picksTomorrowsDefault() {
        val s = LadderSettings(weekdayDefaults = defaults)
        assertEquals(LocalDateTime.of(2026, 10, 10, 8, 0), Fallback.pick(s, fridayNight))
    }

    @Test
    fun noDefaultMeansNoAlarm() {
        val s = LadderSettings(weekdayDefaults = defaults)
        assertNull(Fallback.pick(s, LocalDateTime.of(2026, 10, 10, 22, 30))) // Sunday has none
    }

    @Test
    fun disabledMeansNoAlarm() {
        assertNull(Fallback.pick(LadderSettings(weekdayDefaults = defaults, fallbackEnabled = false), fridayNight))
    }

    @Test
    fun afterMidnightTodaysDefaultCanStillApply() {
        val s = LadderSettings(weekdayDefaults = defaults)
        val now = LocalDateTime.of(2026, 10, 10, 0, 30) // Saturday, default 08:00 still ahead
        assertEquals(LocalDateTime.of(2026, 10, 10, 8, 0), Fallback.pick(s, now))
    }

    @Test
    fun anyPlanForTheFutureCountsEvenIfCancelled() {
        val zone = ZoneId.of("UTC")
        val target = LocalDateTime.of(2026, 10, 10, 6, 40)
        val plan = LadderPlanner.plan(target, LadderSettings(), zone, 0)
        assertEquals(true, Fallback.hasPlanForComingMorning(plan, fridayNight))
        assertEquals(true, Fallback.hasPlanForComingMorning(PlanReducer.cancelAll(plan), fridayNight))
        assertEquals(false, Fallback.hasPlanForComingMorning(null, fridayNight))
        // Yesterday's finished plan does not block tonight's fallback.
        assertEquals(false, Fallback.hasPlanForComingMorning(plan, LocalDateTime.of(2026, 10, 10, 7, 0)))
    }
}

class FallbackMissedTest {
    private val bedtime = LocalTime.of(22, 30)

    @Test
    fun mostRecentOccurrenceLooksBackAcrossMidnight() {
        assertEquals(LocalDateTime.of(2026, 10, 9, 22, 30), Fallback.mostRecentOccurrence(bedtime, LocalDateTime.of(2026, 10, 10, 1, 0)))
        assertEquals(LocalDateTime.of(2026, 10, 9, 22, 30), Fallback.mostRecentOccurrence(bedtime, LocalDateTime.of(2026, 10, 9, 23, 0)))
    }

    @Test
    fun missedWhenPhoneWasOffAtBedtime() {
        val last = LocalDateTime.of(2026, 10, 8, 22, 30) // ran last night, not tonight
        assertEquals(true, Fallback.isMissed(LocalDateTime.of(2026, 10, 9, 23, 15), bedtime, last))
        assertEquals(true, Fallback.isMissed(LocalDateTime.of(2026, 10, 10, 2, 0), bedtime, last))
    }

    @Test
    fun notMissedWhenAlreadyRunOrNotYetDue() {
        val ranTonight = LocalDateTime.of(2026, 10, 9, 22, 30, 1)
        assertEquals(false, Fallback.isMissed(LocalDateTime.of(2026, 10, 9, 23, 15), bedtime, ranTonight))
        // Daytime reboot: last occurrence was last night and it ran, so nothing to catch up.
        assertEquals(false, Fallback.isMissed(LocalDateTime.of(2026, 10, 9, 14, 0), bedtime, LocalDateTime.of(2026, 10, 8, 22, 30)))
    }

    @Test
    fun oldGapsAreIgnored() {
        // Booted at 14:00; the missed 22:30 was 15.5 h ago, too late to be useful.
        assertEquals(false, Fallback.isMissed(LocalDateTime.of(2026, 10, 9, 14, 0), bedtime, null))
        assertEquals(true, Fallback.isMissed(LocalDateTime.of(2026, 10, 9, 23, 0), bedtime, null))
    }
}
