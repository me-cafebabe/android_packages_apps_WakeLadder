package dev.me_cafebabe.wakeladder.alarm

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Registers an alarm with the platform service that can power the phone on, following the
 * contract used by DeskClock. The service keeps a single alarm, so callers register only
 * the next due instant.
 */
interface PowerOffAlarmGateway {
    /** True if the service exists on this device and our permission is granted. */
    val isAvailable: Boolean
    fun set(timeMs: Long)
    fun cancel(timeMs: Long)
}

class QcomPowerOffAlarmGateway(private val context: Context) : PowerOffAlarmGateway {

    /** The service package is installed (the feature can exist on this device). */
    val isInstalled: Boolean
        get() = runCatching { context.packageManager.getPackageInfo(SERVICE_PACKAGE, 0) }.isSuccess

    val hasPermission: Boolean
        get() = context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    override val isAvailable: Boolean get() = isInstalled && hasPermission

    override fun set(timeMs: Long) = send(ACTION_SET, timeMs)
    override fun cancel(timeMs: Long) = send(ACTION_CANCEL, timeMs)

    private fun send(action: String, timeMs: Long) {
        context.sendBroadcast(
            Intent(action)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .setPackage(SERVICE_PACKAGE)
                .putExtra(EXTRA_TIME, timeMs),
        )
    }

    companion object {
        const val PERMISSION = "org.codeaurora.permission.POWER_OFF_ALARM"
        const val SERVICE_PACKAGE = "com.qualcomm.qti.poweroffalarm"
        const val ACTION_SET = "org.codeaurora.poweroffalarm.action.SET_ALARM"
        const val ACTION_CANCEL = "org.codeaurora.poweroffalarm.action.CANCEL_ALARM"
        const val ACTION_UPDATE = "org.codeaurora.poweroffalarm.action.UPDATE_ALARM"
        const val EXTRA_TIME = "time"
        const val EXTRA_STATUS = "status"
        const val EXTRA_SNOOZE_TIME = "snooze_time"
        const val STATUS_SNOOZE = 2
        const val STATUS_DISMISS = 3
    }
}
