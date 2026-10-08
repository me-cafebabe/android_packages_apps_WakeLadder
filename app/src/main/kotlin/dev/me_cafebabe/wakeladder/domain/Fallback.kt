package dev.me_cafebabe.wakeladder.domain

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/** Decides whether the bedtime routine should set alarms on the user's behalf. */
object Fallback {

    /**
     * True if the user already dealt with the coming morning, including by cancelling it
     * (a cancelled plan is a deliberate "skip", so it must not be recreated).
     */
    fun hasPlanForComingMorning(plan: LadderPlan?, now: LocalDateTime): Boolean =
        plan != null && plan.target.isAfter(now)

    /** Latest occurrence of [time] that is not after [now]. */
    fun mostRecentOccurrence(time: LocalTime, now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate().atTime(time)
        return if (today.isAfter(now)) today.minusDays(1) else today
    }

    /**
     * True if the bedtime routine should have run since it last did but did not, e.g. because
     * the phone was off at the bedtime time. Occurrences older than [maxAge] are ignored so an
     * old gap (long-off phone, fresh install) does not trigger a pointless late-morning run.
     */
    fun isMissed(
        now: LocalDateTime,
        bedtime: LocalTime,
        lastRun: LocalDateTime?,
        maxAge: Duration = Duration.ofHours(12),
    ): Boolean {
        val due = mostRecentOccurrence(bedtime, now)
        if (Duration.between(due, now) > maxAge) return false
        return lastRun == null || lastRun.isBefore(due)
    }

    /**
     * The wake time to set automatically: the first of today/tomorrow that has a weekday
     * default still in the future. Null if the fallback is off or no default applies, so days
     * without a default never get a surprise alarm.
     */
    fun pick(settings: LadderSettings, now: LocalDateTime): LocalDateTime? {
        if (!settings.fallbackEnabled) return null
        for (offset in 0L..1L) {
            val day = now.toLocalDate().plusDays(offset)
            val time = settings.weekdayDefaults[day.dayOfWeek] ?: continue
            val at = day.atTime(time)
            if (at.isAfter(now)) return at
        }
        return null
    }
}
