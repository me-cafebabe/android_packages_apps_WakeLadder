package dev.me_cafebabe.wakeladder.alarm

import android.content.Context
import dev.me_cafebabe.wakeladder.app
import dev.me_cafebabe.wakeladder.domain.Fallback
import java.time.LocalDateTime

/**
 * Runs at the bedtime time. If the user has not dealt with the coming morning it either sets
 * alarms from the weekday default (fallback) or, failing that, asks for a wake time.
 */
object BedtimeRoutine {
    /**
     * After boot: run the routine if its time passed while the phone was off. Harmless when
     * the user already set alarms, since [run] does nothing then.
     */
    suspend fun catchUp(context: Context) {
        val store = context.app.store
        val settings = store.settingsNow()
        if (!settings.bedtimeNudgeEnabled && !settings.fallbackEnabled) return
        val last = store.lastRoutineMs()?.let {
            LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), java.time.ZoneId.systemDefault())
        }
        if (Fallback.isMissed(LocalDateTime.now(), settings.bedtimeNudgeTime, last)) run(context)
    }

    suspend fun run(context: Context) {
        val app = context.app
        val settings = app.store.settingsNow()
        val now = LocalDateTime.now()
        app.store.setLastRoutineMs(System.currentTimeMillis())
        if (Fallback.hasPlanForComingMorning(app.store.planNow(), now)) return

        val target = Fallback.pick(settings, now)
        if (target != null) {
            app.scheduler.setWakeTime(target, auto = true)
            Notifications.showFallbackSet(context, target.toLocalTime())
        } else if (settings.bedtimeNudgeEnabled) {
            Notifications.showBedtimeNudge(context)
        }
    }
}
