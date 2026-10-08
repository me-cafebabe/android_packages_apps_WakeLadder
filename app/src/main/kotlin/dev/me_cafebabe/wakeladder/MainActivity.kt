package dev.me_cafebabe.wakeladder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.me_cafebabe.wakeladder.ui.HomeScreen
import dev.me_cafebabe.wakeladder.ui.SettingsScreen
import dev.me_cafebabe.wakeladder.ui.WakeLadderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WakeLadderTheme {
                var inSettings by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = inSettings) { inSettings = false }
                if (inSettings) SettingsScreen(onBack = { inSettings = false })
                else HomeScreen(onOpenSettings = { inSettings = true })
            }
        }
    }
}
