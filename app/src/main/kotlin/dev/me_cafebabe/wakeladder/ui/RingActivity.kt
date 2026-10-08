package dev.me_cafebabe.wakeladder.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.me_cafebabe.wakeladder.R
import dev.me_cafebabe.wakeladder.alarm.Actions
import dev.me_cafebabe.wakeladder.alarm.Notifications
import dev.me_cafebabe.wakeladder.alarm.RingService
import dev.me_cafebabe.wakeladder.app
import dev.me_cafebabe.wakeladder.domain.DismissTask
import dev.me_cafebabe.wakeladder.domain.FollowUpState
import dev.me_cafebabe.wakeladder.domain.LadderPlan
import dev.me_cafebabe.wakeladder.domain.LadderSettings
import dev.me_cafebabe.wakeladder.ui.task.MathTask
import dev.me_cafebabe.wakeladder.ui.task.ShakeTask
import dev.me_cafebabe.wakeladder.ui.task.StepsTask
import java.time.LocalTime

/** Full-screen ring UI shown over the lock screen. Finishes by itself when nothing rings. */
class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            WakeLadderTheme {
                val plan by app.store.plan.collectAsStateWithLifecycle(initialValue = null)
                val settings by app.store.settings.collectAsStateWithLifecycle(initialValue = LadderSettings())
                val p = plan
                val rung = p?.ringingRung()
                val followUp = p?.followUp == FollowUpState.RINGING
                // Nothing ringing (silenced from the notification, timed out, ...): leave.
                LaunchedEffect(p, rung, followUp) { if (p != null && rung == null && !followUp) finish() }
                if (p != null && (rung != null || followUp)) {
                    RingScreen(p, settings, rung?.index ?: -1, followUp)
                }
            }
        }
    }

    @Composable
    private fun RingScreen(plan: LadderPlan, settings: LadderSettings, index: Int, followUp: Boolean) {
        val isLast = followUp || index == plan.lastRung.index
        val task = if (followUp) DismissTask.NONE else settings.taskFor(index)
        var doingTask by remember { mutableStateOf(false) }
        val total = plan.rungs.maxOf { it.index } + 1

        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    Notifications.timeText(this@RingActivity, LocalTime.now()),
                    style = MaterialTheme.typography.displayLarge,
                )
                Text(
                    if (followUp) stringResource(R.string.follow_up_title)
                    else stringResource(R.string.ring_subtitle, index + 1, total),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(32.dp))

                if (doingTask) {
                    val done = { RingService.send(this@RingActivity, Actions.CONFIRM_UP, index) }
                    when (task) {
                        DismissTask.MATH -> MathTask(done)
                        DismissTask.SHAKE -> ShakeTask(done)
                        DismissTask.STEPS -> StepsTask(done)
                        DismissTask.NONE -> Unit
                    }
                } else if (followUp) {
                    Button(
                        onClick = { RingService.send(this@RingActivity, Actions.FOLLOW_UP_OK) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.action_yes_up)) }
                } else {
                    Button(
                        onClick = {
                            if (task == DismissTask.NONE) RingService.send(this@RingActivity, Actions.CONFIRM_UP, index)
                            else doingTask = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(if (isLast) R.string.action_dismiss else R.string.action_im_up)) }
                    if (!isLast) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { RingService.send(this@RingActivity, Actions.SILENCE, index) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.action_silence)) }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.ring_silence_hint),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
