package dev.me_cafebabe.wakeladder.ui.task

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.me_cafebabe.wakeladder.R

/**
 * Walk [STEPS] steps. Needs the step detector and the activity-recognition permission; if
 * either is missing the task completes immediately, because a task must never trap the user.
 */
@Composable
fun StepsTask(onDone: () -> Unit) {
    val context = LocalContext.current
    val sm = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember { sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) }
    var granted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    var steps by remember { mutableIntStateOf(0) }

    LaunchedEffect(sensor, asked) { if (sensor == null || (asked && !granted)) onDone() }

    DisposableEffect(granted) {
        if (!granted || sensor == null) return@DisposableEffect onDispose { }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                steps++
                if (steps >= STEPS) onDone()
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sm.unregisterListener(listener) }
    }

    Column {
        Text(stringResource(R.string.task_steps, STEPS), style = MaterialTheme.typography.bodyLarge)
        if (granted) LinearProgressIndicator(progress = { steps / STEPS.toFloat() })
        else Button(onClick = { launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }) {
            Text(stringResource(R.string.grant_permission))
        }
    }
}

private const val STEPS = 20
