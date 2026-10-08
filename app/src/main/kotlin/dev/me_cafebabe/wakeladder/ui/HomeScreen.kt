package dev.me_cafebabe.wakeladder.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.me_cafebabe.wakeladder.R
import dev.me_cafebabe.wakeladder.alarm.Notifications
import dev.me_cafebabe.wakeladder.alarm.QcomPowerOffAlarmGateway
import dev.me_cafebabe.wakeladder.app
import dev.me_cafebabe.wakeladder.domain.LadderPlan
import dev.me_cafebabe.wakeladder.domain.LadderPlanner
import dev.me_cafebabe.wakeladder.domain.LadderSettings
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by context.app.store.settings.collectAsStateWithLifecycle(LadderSettings())
    val plan by context.app.store.plan.collectAsStateWithLifecycle(null)
    val now by produceState(LocalDateTime.now()) {
        while (true) { delay(15_000); value = LocalDateTime.now() }
    }

    val active = plan?.takeIf { !it.isFinished && it.target.isAfter(now) }
    var draftSecond by rememberSaveable { mutableIntStateOf(-1) }
    val suggestion = settings.weekdayDefaults[now.toLocalDate().plusDays(1).dayOfWeek] ?: settings.lastUsedTime
    val time = when {
        draftSecond >= 0 -> LocalTime.ofSecondOfDay(draftSecond.toLong())
        active != null -> active.target.toLocalTime()
        else -> suggestion
    }
    var picking by remember { mutableStateOf(false) }

    val target = LadderPlanner.nextOccurrence(time, now)
    val preview = LadderPlanner.plan(target, settings, ZoneId.systemDefault(), 0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HealthChecks(settings)

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(
                        stringResource(R.string.wake_by),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        Notifications.timeText(context, time),
                        style = MaterialTheme.typography.displayLarge,
                        modifier = Modifier.clickable { picking = true },
                    )
                    Text(
                        if (target.toLocalDate() == now.toLocalDate()) stringResource(R.string.today)
                        else stringResource(R.string.tomorrow),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { draftSecond = time.minusMinutes(15).toSecondOfDay() }) { Text("−15") }
                        TextButton(onClick = { draftSecond = time.plusMinutes(15).toSecondOfDay() }) { Text("+15") }
                        TextButton(onClick = { picking = true }) { Text(stringResource(R.string.pick_time)) }
                    }
                }
            }

            RungPreview(preview, settings, now)
            SleepEstimate(now, target, preview)

            Button(
                onClick = {
                    scope.launch {
                        context.app.scheduler.setWakeTime(target)
                        draftSecond = -1
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (active == null) R.string.set_ladder else R.string.update_ladder))
            }
            if (active != null) {
                if (active.auto) Text(stringResource(R.string.auto_set_note), style = MaterialTheme.typography.bodySmall)
                NextRing(active, now)
                OutlinedButton(
                    onClick = { scope.launch { context.app.scheduler.cancelLadder() } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.cancel_ladder)) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (picking) {
        TimePickerDialog(
            initial = time,
            onDismiss = { picking = false },
            onConfirm = { draftSecond = it.toSecondOfDay(); picking = false },
        )
    }
}

@Composable
private fun RungPreview(preview: LadderPlan, settings: LadderSettings, now: LocalDateTime) {
    val context = LocalContext.current
    Column {
        Text(stringResource(R.string.ladder), style = MaterialTheme.typography.titleMedium)
        preview.rungs.forEach { r ->
            val t = java.time.Instant.ofEpochMilli(r.timeMs).atZone(ZoneId.systemDefault()).toLocalTime()
            val style = settings.styleFor(r.index)
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${r.index + 1}  ·  ${Notifications.timeText(context, t)}", style = MaterialTheme.typography.bodyLarge)
                Text("${style.volumePercent}%", style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (preview.rungs.size < settings.rungCount) {
            Text(stringResource(R.string.rungs_skipped), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SleepEstimate(now: LocalDateTime, target: LocalDateTime, preview: LadderPlan) {
    val toTarget = Duration.between(now, target)
    val first = preview.rungs.firstOrNull()
        ?.let { java.time.Instant.ofEpochMilli(it.timeMs).atZone(ZoneId.systemDefault()).toLocalDateTime() }
    val toFirst = first?.let { Duration.between(now, it) }
    Text(
        stringResource(R.string.sleep_estimate, duration(toTarget), toFirst?.let { duration(it) } ?: "—"),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun NextRing(plan: LadderPlan, now: LocalDateTime) {
    val due = plan.nextDueMs() ?: return
    val at = java.time.Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).toLocalDateTime()
    Text(
        stringResource(R.string.next_ring, Notifications.timeText(LocalContext.current, at.toLocalTime()), duration(Duration.between(now, at))),
        style = MaterialTheme.typography.titleSmall,
    )
}

@Composable
private fun duration(d: Duration): String {
    val minutes = d.toMinutes().coerceAtLeast(0)
    return stringResource(R.string.duration_hm, minutes / 60, minutes % 60)
}

/** Things that would make the alarm fail; shown only when something is wrong. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HealthChecks(settings: LadderSettings) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { refresh++; onPauseOrDispose { } }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val poLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    @Suppress("UNUSED_EXPRESSION") refresh
    val nm = context.getSystemService(NotificationManager::class.java)
    val issues = buildList {
        if (!nm.areNotificationsEnabled()) {
            add(R.string.check_notifications to { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) })
        }
        if (!nm.canUseFullScreenIntent()) {
            add(R.string.check_fullscreen to {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")),
                )
            })
        }
        val pm = context.getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
            add(R.string.check_battery to {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                )
            })
        }
        val po = context.app.powerOff
        if (settings.powerOffAlarm && po.isInstalled && !po.hasPermission) {
            add(R.string.check_power_off to { poLauncher.launch(QcomPowerOffAlarmGateway.PERMISSION) })
        }
    }
    if (issues.isEmpty()) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.checks_title), style = MaterialTheme.typography.titleMedium)
            issues.forEach { (text, fix) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(text), modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                    TextButton(onClick = fix) { Text(stringResource(R.string.fix)) }
                }
            }
        }
    }
}
