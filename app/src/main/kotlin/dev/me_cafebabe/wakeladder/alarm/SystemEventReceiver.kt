package dev.me_cafebabe.wakeladder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.me_cafebabe.wakeladder.app
import dev.me_cafebabe.wakeladder.domain.PlanReducer
import dev.me_cafebabe.wakeladder.domain.RungState
import kotlinx.coroutines.launch

/**
 * Re-arms everything after boot / app update / clock or zone change, and handles the
 * status callback of the power-off alarm service.
 */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.app
        val pending = goAsync()
        app.scope.launch {
            try {
                when (intent.action) {
                    Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED ->
                        app.scheduler.replanForClockChange()
                    QcomPowerOffAlarmGateway.ACTION_UPDATE -> onPowerOffStatus(context, intent)
                    else -> app.scheduler.reschedule()
                }
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * The power-off service rang the alarm while the phone was off and reports what the user did.
     * Both "snooze" and "dismiss" silence that rung only: the ladder's next rung is the snooze.
     * Dismissing the last rung also counts as being up. The time must match one of our own
     * instants, otherwise the broadcast is ignored.
     */
    private suspend fun onPowerOffStatus(context: Context, intent: Intent) {
        val app = context.app
        val time = intent.getLongExtra(QcomPowerOffAlarmGateway.EXTRA_TIME, 0L)
        if (time == 0L) return
        val settings = app.store.settingsNow()
        val status = intent.getIntExtra(QcomPowerOffAlarmGateway.EXTRA_STATUS, 0)
        val now = System.currentTimeMillis()
        app.store.updatePlan { plan ->
            val rung = plan?.rungs?.firstOrNull { it.timeMs == time }
            when {
                plan == null -> null
                rung == null -> plan
                status == QcomPowerOffAlarmGateway.STATUS_DISMISS && rung.index == plan.lastRung.index ->
                    PlanReducer.confirmUp(plan, settings, now)
                status == QcomPowerOffAlarmGateway.STATUS_DISMISS ||
                    status == QcomPowerOffAlarmGateway.STATUS_SNOOZE ->
                    plan.copy(rungs = plan.rungs.map {
                        if (it.index == rung.index && it.state != RungState.CANCELLED)
                            it.copy(state = RungState.SILENCED) else it
                    })
                else -> plan
            }
        }
        app.store.setPoweroffRegistered(null) // that alarm is consumed; reschedule registers the next
        app.scheduler.reschedule()
    }
}
