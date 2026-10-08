package dev.me_cafebabe.wakeladder.ui.task

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.me_cafebabe.wakeladder.R
import kotlin.math.sqrt

/** Shake the phone [SHAKES] times. */
@Composable
fun ShakeTask(onDone: () -> Unit) {
    val context = LocalContext.current
    var count by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var last = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]) /
                    SensorManager.GRAVITY_EARTH
                val now = System.currentTimeMillis()
                if (g > THRESHOLD_G && now - last > 400) {
                    last = now
                    count++
                    if (count >= SHAKES) onDone()
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }
    Column {
        Text(stringResource(R.string.task_shake, SHAKES), style = MaterialTheme.typography.bodyLarge)
        LinearProgressIndicator(progress = { count / SHAKES.toFloat() })
    }
}

private const val SHAKES = 25
private const val THRESHOLD_G = 2.2f
