package dev.me_cafebabe.wakeladder

import android.app.Application
import dev.me_cafebabe.wakeladder.alarm.AlarmScheduler
import dev.me_cafebabe.wakeladder.alarm.Notifications
import dev.me_cafebabe.wakeladder.alarm.QcomPowerOffAlarmGateway
import dev.me_cafebabe.wakeladder.data.AppStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-rolled DI root; receivers and services reach it through the application context. */
class WakeLadderApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val store by lazy { AppStore(this) }
    val powerOff by lazy { QcomPowerOffAlarmGateway(this) }
    val scheduler by lazy { AlarmScheduler(this, store, powerOff) }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}

val android.content.Context.app: WakeLadderApp get() = applicationContext as WakeLadderApp
