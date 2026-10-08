package dev.me_cafebabe.wakeladder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.me_cafebabe.wakeladder.MainActivity
import dev.me_cafebabe.wakeladder.data.AppStore
import dev.me_cafebabe.wakeladder.domain.FollowUpState
import dev.me_cafebabe.wakeladder.domain.LadderPlanner
import dev.me_cafebabe.wakeladder.domain.LadderSettings
import dev.me_cafebabe.wakeladder.domain.PlanReducer
import dev.me_cafebabe.wakeladder.domain.RungState
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Turns the stored plan into AlarmManager alarms. [reschedule] is idempotent: it always
 * rebuilds everything from the store, so every event that may invalidate alarms
 * (boot, time change, settings change, rung fired) can simply call it.
 */
class AlarmScheduler(
    private val context: Context,
    private val store: AppStore,
    private val powerOff: PowerOffAlarmGateway,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val mutex = Mutex()

    /** Creates the ladder ending at [target] (wall clock, current zone) and arms it. */
    suspend fun setWakeTime(target: LocalDateTime) {
        val settings = store.settingsNow()
        val now = nowMs()
        store.updatePlan { LadderPlanner.plan(target, settings, ZoneId.systemDefault(), now) }
        store.updateSettings { it.copy(lastUsedTime = target.toLocalTime()) }
        reschedule()
    }

    /** Cancels everything still pending (e.g. "I woke up early on my own"). */
    suspend fun cancelLadder() {
        store.updatePlan { it?.let(PlanReducer::cancelAll) }
        reschedule()
    }

    /** Re-derives rung instants after a time-zone or clock change. */
    suspend fun replanForClockChange() {
        val settings = store.settingsNow()
        val now = nowMs()
        store.updatePlan { it?.let { p -> LadderPlanner.replan(p, settings, ZoneId.systemDefault(), now) } }
        reschedule()
    }

    suspend fun reschedule() = mutex.withLock {
        val settings = store.settingsNow()
        val now = nowMs()
        val plan = store.updatePlan { it?.let { p -> PlanReducer.settleMissed(p, now, MISSED_GRACE_MS) } }

        for (i in 0 until LadderSettings.MAX_RUNGS) alarmManager.cancel(rungIntent(i))
        alarmManager.cancel(followUpIntent())

        plan?.rungs?.filter { it.state == RungState.SCHEDULED }?.forEach { rung ->
            // A rung that is slightly overdue (e.g. after reboot) fires right away.
            val at = maxOf(rung.timeMs, now + OVERDUE_DELAY_MS)
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(at, showIntent()),
                rungIntent(rung.index),
            )
        }
        if (plan != null && plan.followUp == FollowUpState.SCHEDULED) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                maxOf(plan.followUpAtMs, now + OVERDUE_DELAY_MS),
                followUpIntent(),
            )
        }

        scheduleNudge(settings, now)
        syncPowerOff(settings, plan?.nextDueMs())
    }

    private suspend fun syncPowerOff(settings: LadderSettings, desired: Long?) {
        val wanted = if (settings.powerOffAlarm && powerOff.isAvailable) desired else null
        val registered = store.poweroffRegistered()
        if (registered == wanted) return
        if (registered != null) powerOff.cancel(registered)
        if (wanted != null) powerOff.set(wanted)
        store.setPoweroffRegistered(wanted)
    }

    private fun scheduleNudge(settings: LadderSettings, now: Long) {
        alarmManager.cancel(nudgeIntent())
        if (!settings.bedtimeNudgeEnabled) return
        val next = LadderPlanner.nextOccurrence(
            settings.bedtimeNudgeTime,
            java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), ZoneId.systemDefault()),
        )
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            nudgeIntent(),
        )
    }

    private fun rungIntent(index: Int): PendingIntent = PendingIntent.getBroadcast(
        context, REQ_RUNG + index,
        Intent(Actions.RUNG).setClass(context, AlarmReceiver::class.java)
            .putExtra(Actions.EXTRA_INDEX, index),
        FLAGS,
    )

    private fun followUpIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, REQ_FOLLOW_UP,
        Intent(Actions.FOLLOW_UP).setClass(context, AlarmReceiver::class.java), FLAGS,
    )

    private fun nudgeIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, REQ_NUDGE,
        Intent(Actions.NUDGE).setClass(context, AlarmReceiver::class.java), FLAGS,
    )

    private fun showIntent(): PendingIntent = PendingIntent.getActivity(
        context, REQ_SHOW, Intent(context, MainActivity::class.java), FLAGS,
    )

    companion object {
        const val MISSED_GRACE_MS = 15 * 60_000L
        private const val OVERDUE_DELAY_MS = 1_500L
        private const val REQ_RUNG = 100
        private const val REQ_FOLLOW_UP = 200
        private const val REQ_NUDGE = 201
        private const val REQ_SHOW = 202
        private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}
