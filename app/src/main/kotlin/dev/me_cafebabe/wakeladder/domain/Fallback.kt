package dev.me_cafebabe.wakeladder.domain

import java.time.LocalDateTime

/** Decides whether the bedtime routine should set alarms on the user's behalf. */
object Fallback {

    /**
     * True if the user already dealt with the coming morning, including by cancelling it
     * (a cancelled plan is a deliberate "skip", so it must not be recreated).
     */
    fun hasPlanForComingMorning(plan: LadderPlan?, now: LocalDateTime): Boolean =
        plan != null && plan.target.isAfter(now)

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
