package dev.me_cafebabe.wakeladder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.me_cafebabe.wakeladder.app
import dev.me_cafebabe.wakeladder.domain.PlanReducer
import kotlinx.coroutines.launch

/** Entry point for rung, follow-up and bedtime-nudge alarms. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.app
        val pending = goAsync()
        app.scope.launch {
            try {
                when (intent.action) {
                    Actions.RUNG -> {
                        val index = intent.getIntExtra(Actions.EXTRA_INDEX, -1)
                        val plan = app.store.updatePlan { it?.let { p -> PlanReducer.onRungFired(p, index) } }
                        if (plan?.ringingRung()?.index == index) {
                            RingService.ring(context, Actions.MODE_RUNG, index)
                        }
                    }
                    Actions.FOLLOW_UP -> {
                        val plan = app.store.updatePlan { it?.let(PlanReducer::onFollowUpFired) }
                        if (plan?.followUp == dev.me_cafebabe.wakeladder.domain.FollowUpState.RINGING) {
                            RingService.ring(context, Actions.MODE_FOLLOW_UP, -1)
                        }
                    }
                    Actions.NUDGE -> Notifications.showBedtimeNudge(context)
                    Actions.NUDGE_ACCEPT -> {
                        val sod = intent.getIntExtra(Actions.EXTRA_TIME_MS, -1)
                        if (sod >= 0) app.scheduler.setWakeTime(Notifications.targetFor(sod))
                        Notifications.cancelNudge(context)
                    }
                }
                app.scheduler.reschedule()
            } finally {
                pending.finish()
            }
        }
    }
}
