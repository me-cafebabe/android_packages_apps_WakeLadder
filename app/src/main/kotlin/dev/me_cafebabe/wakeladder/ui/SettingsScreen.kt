package dev.me_cafebabe.wakeladder.ui

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.me_cafebabe.wakeladder.R
import dev.me_cafebabe.wakeladder.alarm.Notifications
import dev.me_cafebabe.wakeladder.app
import dev.me_cafebabe.wakeladder.domain.DismissTask
import dev.me_cafebabe.wakeladder.domain.LadderSettings
import dev.me_cafebabe.wakeladder.domain.RungOverride
import dev.me_cafebabe.wakeladder.domain.Sound
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by context.app.store.settings.collectAsStateWithLifecycle(LadderSettings())

    fun update(f: (LadderSettings) -> LadderSettings) {
        scope.launch {
            context.app.store.updateSettings(f)
            context.app.scheduler.reschedule() // nudge / power-off registration depend on settings
        }
    }

    var editRung by remember { mutableStateOf<Int?>(null) }
    var editDay by remember { mutableStateOf<DayOfWeek?>(null) }
    var editNudge by remember { mutableStateOf(false) }
    var soundTarget by remember { mutableStateOf<Int?>(null) } // -1 = global default, else rung index

    fun setSound(target: Int, uri: Uri?) {
        val sound = Sound(uri?.toString())
        if (target < 0) update { it.copy(defaultSound = sound) }
        else update { it.copy(overrides = it.overrides + (target to (it.overrides[target] ?: RungOverride()).copy(sound = sound))) }
    }

    val systemPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri: Uri? = r.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        val target = soundTarget
        if (r.resultCode == android.app.Activity.RESULT_OK && target != null) setSound(target, uri)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = soundTarget
        if (uri != null && target != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            setSound(target, uri)
        }
    }
    fun launchSystemPicker(target: Int, existing: String?) {
        soundTarget = target
        systemPicker.launch(
            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing?.let(Uri::parse)),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            SectionTitle(stringResource(R.string.section_ladder))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Preset(R.string.preset_gentle, s.rungCount == 4 && s.intervalMinutes == 20) { update { LadderSettings.preset(4, 20, it) } }
                Preset(R.string.preset_firm, s.rungCount == 3 && s.intervalMinutes == 10) { update { LadderSettings.preset(3, 10, it) } }
                Preset(R.string.preset_single, s.rungCount == 1) { update { LadderSettings.preset(1, it.intervalMinutes, it) } }
            }
            SliderRow(stringResource(R.string.rung_count), s.rungCount, LadderSettings.MIN_RUNGS..LadderSettings.MAX_RUNGS, { "$it" }) { v -> update { it.copy(rungCount = v) } }
            SliderRow(stringResource(R.string.interval), s.intervalMinutes, LadderSettings.MIN_INTERVAL..LadderSettings.MAX_INTERVAL,
                { stringResource(R.string.minutes_short, it) }, step = 5) { v -> update { it.copy(intervalMinutes = v) } }
            Text(stringResource(R.string.ladder_hint), style = MaterialTheme.typography.bodySmall)

            SectionTitle(stringResource(R.string.section_sound))
            SliderRow(stringResource(R.string.first_volume), s.startVolumePercent, 0..100, { "$it%" }, step = 5) { v -> update { it.copy(startVolumePercent = v) } }
            SliderRow(stringResource(R.string.last_volume), s.endVolumePercent, 0..100, { "$it%" }, step = 5) { v -> update { it.copy(endVolumePercent = v) } }
            SliderRow(stringResource(R.string.ramp), s.rampSeconds, 0..60, { stringResource(R.string.seconds_short, it) }, step = 5) { v -> update { it.copy(rampSeconds = v) } }
            SoundRow(stringResource(R.string.default_sound), s.defaultSound, context,
                onSystem = { launchSystemPicker(-1, s.defaultSound.uri) },
                onFile = { soundTarget = -1; filePicker.launch(arrayOf("audio/*")) },
                onReset = { setSound(-1, null) })
            SwitchRow(stringResource(R.string.vibrate), s.vibrate, stringResource(R.string.vibrate_hint)) { v -> update { it.copy(vibrate = v) } }
            SwitchRow(
                stringResource(R.string.force_volume), s.minStreamPercent != null,
                stringResource(R.string.force_volume_hint),
            ) { v -> update { it.copy(minStreamPercent = if (v) 60 else null) } }
            s.minStreamPercent?.let { pct ->
                SliderRow(stringResource(R.string.force_volume_level), pct, 10..100, { "$it%" }, step = 10) { v -> update { it.copy(minStreamPercent = v) } }
            }
            SliderRow(stringResource(R.string.ring_timeout), s.ringTimeoutMinutes, 1..15, { stringResource(R.string.minutes_short, it) }) { v -> update { it.copy(ringTimeoutMinutes = v) } }

            Text(stringResource(R.string.per_rung), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0 until s.rungCount).forEach { i ->
                    OutlinedButton(onClick = { editRung = i }) {
                        Text("${i + 1} · ${s.styleFor(i).volumePercent}%")
                    }
                }
            }

            SectionTitle(stringResource(R.string.section_dismiss))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DismissTask.entries.forEach { t ->
                    FilterChip(
                        selected = s.dismissTask == t,
                        onClick = { update { it.copy(dismissTask = t) } },
                        label = { Text(stringResource(taskLabel(t))) },
                    )
                }
            }
            if (s.dismissTask != DismissTask.NONE) {
                SwitchRow(stringResource(R.string.task_last_only), s.taskOnLastRungOnly) { v -> update { it.copy(taskOnLastRungOnly = v) } }
            }
            SwitchRow(stringResource(R.string.follow_up_setting), s.followUpEnabled, stringResource(R.string.follow_up_hint)) { v -> update { it.copy(followUpEnabled = v) } }
            if (s.followUpEnabled) {
                SliderRow(stringResource(R.string.follow_up_after), s.followUpMinutes, 3..15, { stringResource(R.string.minutes_short, it) }) { v -> update { it.copy(followUpMinutes = v) } }
            }

            SectionTitle(stringResource(R.string.section_schedule))
            DayOfWeek.entries.forEach { d ->
                Row(
                    Modifier.fillMaxWidth().clickable { editDay = d }.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(d.getDisplayName(TextStyle.FULL, Locale.getDefault()))
                    Text(s.weekdayDefaults[d]?.let { Notifications.timeText(context, it) } ?: stringResource(R.string.not_set))
                }
            }
            Text(stringResource(R.string.weekday_hint), style = MaterialTheme.typography.bodySmall)
            SwitchRow(stringResource(R.string.nudge), s.bedtimeNudgeEnabled, stringResource(R.string.nudge_hint)) { v -> update { it.copy(bedtimeNudgeEnabled = v) } }
            if (s.bedtimeNudgeEnabled) {
                Row(
                    Modifier.fillMaxWidth().clickable { editNudge = true }.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.nudge_time))
                    Text(Notifications.timeText(context, s.bedtimeNudgeTime))
                }
            }

            if (context.app.powerOff.isInstalled) {
                SectionTitle(stringResource(R.string.section_power))
                SwitchRow(stringResource(R.string.power_off), s.powerOffAlarm, stringResource(R.string.power_off_hint)) { v -> update { it.copy(powerOffAlarm = v) } }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 24.dp))
        }
    }

    editRung?.let { i ->
        RungDialog(
            index = i, settings = s, context = context,
            onDismiss = { editRung = null },
            onChange = { o -> update { it.copy(overrides = it.overrides + (i to o)) } },
            onReset = { update { it.copy(overrides = it.overrides - i) } },
            onSystem = { launchSystemPicker(i, s.overrides[i]?.sound?.uri) },
            onFile = { soundTarget = i; filePicker.launch(arrayOf("audio/*")) },
        )
    }
    editDay?.let { d ->
        TimePickerDialog(
            initial = s.weekdayDefaults[d] ?: s.lastUsedTime,
            onDismiss = { editDay = null },
            onClear = { update { it.copy(weekdayDefaults = it.weekdayDefaults - d) }; editDay = null },
            onConfirm = { t -> update { it.copy(weekdayDefaults = it.weekdayDefaults + (d to t)) }; editDay = null },
        )
    }
    if (editNudge) {
        TimePickerDialog(
            initial = s.bedtimeNudgeTime,
            onDismiss = { editNudge = false },
            onConfirm = { t -> update { it.copy(bedtimeNudgeTime = t) }; editNudge = false },
        )
    }
}

@Composable
private fun Preset(label: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(stringResource(label)) })
}

private fun taskLabel(t: DismissTask) = when (t) {
    DismissTask.NONE -> R.string.task_none
    DismissTask.MATH -> R.string.task_math
    DismissTask.SHAKE -> R.string.task_shake_label
    DismissTask.STEPS -> R.string.task_steps_label
}

private fun soundName(context: Context, sound: Sound?): String? {
    val uri = sound?.uri ?: return null
    return runCatching { RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context) }.getOrNull()
}

@Composable
private fun SoundRow(
    title: String, sound: Sound, context: Context,
    onSystem: () -> Unit, onFile: () -> Unit, onReset: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(soundName(context, sound) ?: stringResource(R.string.sound_system_default), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onSystem) { Text(stringResource(R.string.sound_pick)) }
            TextButton(onClick = onFile) { Text(stringResource(R.string.sound_file)) }
            if (sound.uri != null) TextButton(onClick = onReset) { Text(stringResource(R.string.sound_reset)) }
        }
    }
}

@Composable
private fun RungDialog(
    index: Int, settings: LadderSettings, context: Context,
    onDismiss: () -> Unit, onChange: (RungOverride) -> Unit, onReset: () -> Unit,
    onSystem: () -> Unit, onFile: () -> Unit,
) {
    val o = settings.overrides[index] ?: RungOverride()
    val style = settings.styleFor(index)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rung_title, index + 1)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SliderRow(stringResource(R.string.volume), style.volumePercent, 0..100, { "$it%" }, step = 5) { v -> onChange(o.copy(volumePercent = v)) }
                SoundRow(stringResource(R.string.sound), o.sound ?: settings.defaultSound, context, onSystem, onFile) {
                    onChange(o.copy(sound = null))
                }
                SwitchRow(stringResource(R.string.vibrate), style.vibrate) { v -> onChange(o.copy(vibrate = v)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = { onReset(); onDismiss() }) { Text(stringResource(R.string.reset_rung)) } },
    )
}
