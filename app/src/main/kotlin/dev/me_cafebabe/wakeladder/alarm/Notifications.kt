package dev.me_cafebabe.wakeladder.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.core.app.NotificationCompat
import dev.me_cafebabe.wakeladder.MainActivity
import dev.me_cafebabe.wakeladder.R
import dev.me_cafebabe.wakeladder.app
import dev.me_cafebabe.wakeladder.domain.DismissTask
import dev.me_cafebabe.wakeladder.domain.LadderPlanner
import dev.me_cafebabe.wakeladder.domain.LadderSettings
import dev.me_cafebabe.wakeladder.ui.RingActivity
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object Notifications {
    const val CHANNEL_RING = "ring"
    const val CHANNEL_NUDGE = "nudge"
    const val ID_RING = 1
    const val ID_NUDGE = 2

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RING, context.getString(R.string.channel_ring), NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    setSound(null, null) // the service plays the sound itself
                    setBypassDnd(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_NUDGE, context.getString(R.string.channel_nudge), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun timeText(context: Context, time: LocalTime): String =
        time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(context.resources.configuration.locales[0]))

    fun ring(
        context: Context,
        mode: String,
        index: Int,
        rungCount: Int,
        settings: LadderSettings,
    ): Notification {
        val isFollowUp = mode == Actions.MODE_FOLLOW_UP
        val isLast = isFollowUp || index == rungCount - 1
        val needsTask = !isFollowUp && settings.taskFor(index) != DismissTask.NONE

        val open = PendingIntent.getActivity(
            context, 10, Intent(context, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(context, CHANNEL_RING)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
        if (isFollowUp) {
            b.setContentTitle(context.getString(R.string.follow_up_title))
                .setContentText(context.getString(R.string.follow_up_text))
                .addAction(0, context.getString(R.string.action_yes_up), service(context, Actions.FOLLOW_UP_OK, index))
        } else {
            b.setContentTitle(context.getString(R.string.ring_title))
                .setContentText(context.getString(R.string.ring_subtitle, index + 1, rungCount))
            if (!isLast) b.addAction(0, context.getString(R.string.action_silence), service(context, Actions.SILENCE, index))
            val upLabel = context.getString(if (isLast) R.string.action_dismiss else R.string.action_im_up)
            // A required task must be done in the activity; a plain confirmation can be done here.
            b.addAction(0, upLabel, if (needsTask) open else service(context, Actions.CONFIRM_UP, index))
        }
        return b.build()
    }

    private fun service(context: Context, action: String, index: Int): PendingIntent =
        PendingIntent.getService(
            context, action.hashCode() xor index,
            Intent(context, RingService::class.java).setAction(action).putExtra(Actions.EXTRA_INDEX, index),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Bedtime prompt. Skipped when a ladder for the coming morning is already set. */
    suspend fun showBedtimeNudge(context: Context) {
        val app = context.app
        val settings = app.store.settingsNow()
        val now = LocalDateTime.now()

        val tomorrow = now.toLocalDate().plusDays(1).dayOfWeek
        val suggestion = settings.weekdayDefaults[tomorrow] ?: settings.lastUsedTime
        val open = PendingIntent.getActivity(
            context, 11, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val accept = PendingIntent.getBroadcast(
            context, 12,
            Intent(Actions.NUDGE_ACCEPT).setClass(context, AlarmReceiver::class.java)
                .putExtra(Actions.EXTRA_TIME_MS, suggestion.toSecondOfDay()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val bm = context.getSystemService(BatteryManager::class.java)
        val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val text = buildString {
            append(context.getString(R.string.nudge_text))
            if (battery in 1..19 && !bm.isCharging) {
                append('\n').append(context.getString(R.string.nudge_battery_low, battery))
            }
        }
        val n = NotificationCompat.Builder(context, CHANNEL_NUDGE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.nudge_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.nudge_action_use, timeText(context, suggestion)), accept)
            .addAction(0, context.getString(R.string.nudge_action_pick), open)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ID_NUDGE, n)
    }

    /** Tells the user the fallback set their alarms, so an automatic alarm is never a surprise. */
    fun showFallbackSet(context: Context, time: LocalTime) {
        val open = PendingIntent.getActivity(
            context, 13, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_NUDGE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.fallback_set_title, timeText(context, time)))
            .setContentText(context.getString(R.string.fallback_set_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ID_NUDGE, n)
    }

    fun cancelNudge(context: Context) =
        context.getSystemService(NotificationManager::class.java).cancel(ID_NUDGE)

    /** Next occurrence of a nudge-accepted time. */
    fun targetFor(secondOfDay: Int): LocalDateTime =
        LadderPlanner.nextOccurrence(LocalTime.ofSecondOfDay(secondOfDay.toLong()), LocalDateTime.now())
}
