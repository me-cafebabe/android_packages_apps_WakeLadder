package dev.me_cafebabe.wakeladder.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import dev.me_cafebabe.wakeladder.R
import dev.me_cafebabe.wakeladder.app
import dev.me_cafebabe.wakeladder.domain.LadderPlan
import dev.me_cafebabe.wakeladder.domain.LadderSettings
import dev.me_cafebabe.wakeladder.domain.PlanReducer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Plays one ringing rung (or the "still up?" re-check). All state changes go through
 * [PlanReducer] and the store, so the service itself holds only audio resources.
 *
 * The sound never fails silent: chosen sound -> system default alarm -> bundled tone.
 */
class RingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val handler = Handler(Looper.getMainLooper())

    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var savedStreamVolume: Int? = null
    @Volatile private var mode: String? = null
    private var index = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val idx = intent?.getIntExtra(Actions.EXTRA_INDEX, -1) ?: -1
        when (intent?.action) {
            Actions.RING -> beginRing(intent.getStringExtra(Actions.EXTRA_MODE) ?: Actions.MODE_RUNG, idx)
            Actions.SILENCE -> finish { plan, _, _ -> PlanReducer.silence(plan, idx) }
            Actions.CONFIRM_UP -> finish { plan, settings, now -> PlanReducer.confirmUp(plan, settings, now) }
            Actions.FOLLOW_UP_OK -> finish { plan, _, _ -> PlanReducer.followUpAcknowledged(plan) }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseAudio()
        scope.cancel()
        super.onDestroy()
    }

    // ---- ringing ------------------------------------------------------------------------

    private fun beginRing(newMode: String, newIndex: Int) {
        val app = app
        // The store is tiny and the 5 s foreground deadline is generous; read it synchronously.
        val (settings, plan) = runBlocking { app.store.settingsNow() to app.store.planNow() }
        val rungCount = plan?.rungs?.maxOfOrNull { it.index }?.plus(1) ?: settings.rungCount

        startForeground(
            Notifications.ID_RING,
            Notifications.ring(this, newMode, newIndex, rungCount, settings),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )

        // A newer rung takes over: the one that was still ringing counts as silenced.
        if (player != null || mode != null) {
            releaseAudio()
            if (mode == Actions.MODE_RUNG && index != newIndex) {
                runBlocking { app.store.updatePlan { p -> p?.let { PlanReducer.silence(it, index) } } }
            }
        }
        mode = newMode
        index = newIndex

        val styleIndex = if (newMode == Actions.MODE_FOLLOW_UP) settings.rungCount - 1 else newIndex
        val style = settings.styleFor(styleIndex)
        acquireWakeLock(settings)
        ensureAudible(settings)
        requestFocus()
        play(style.sound.uri, style.volumePercent / 100f, settings.rampSeconds)
        if (style.vibrate) vibrate()
        handler.postDelayed({ onTimeout() }, settings.ringTimeoutMinutes * 60_000L)
    }

    private fun onTimeout() {
        val m = mode
        finish { plan, settings, now ->
            when {
                m == Actions.MODE_FOLLOW_UP -> PlanReducer.followUpTimedOut(plan, settings, now)
                plan.ringingRung()?.index == plan.lastRung.index ->
                    PlanReducer.lastRungTimedOut(plan, settings, now)
                else -> plan.ringingRung()?.let { PlanReducer.silence(plan, it.index) } ?: plan
            }
        }
    }

    /** Applies [transition] to the stored plan, stops sound, and re-arms the next alarms. */
    private fun finish(transition: (plan: LadderPlan, settings: LadderSettings, nowMs: Long) -> LadderPlan) {
        val app = app
        releaseAudio()
        mode = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        scope.launch {
            val settings = app.store.settingsNow()
            val now = System.currentTimeMillis()
            app.store.updatePlan { p -> p?.let { transition(it, settings, now) } }
            app.scheduler.reschedule()
            // A following rung may have started ringing meanwhile; then this service must live on.
            if (mode == null) stopSelf()
        }
    }

    // ---- audio --------------------------------------------------------------------------

    private fun candidates(chosen: String?): List<Uri> = listOfNotNull(
        chosen?.let(Uri::parse),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
        Uri.parse("android.resource://$packageName/${R.raw.fallback_alarm}"),
    )

    private fun play(chosen: String?, gain: Float, rampSeconds: Int) {
        tryPlay(candidates(chosen), 0, gain, rampSeconds)
    }

    private fun tryPlay(uris: List<Uri>, i: Int, gain: Float, rampSeconds: Int) {
        if (i >= uris.size) return
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            mp.setDataSource(this, uris[i])
            mp.isLooping = true
            mp.setOnErrorListener { m, _, _ ->
                m.release()
                if (player === m) player = null
                tryPlay(uris, i + 1, gain, rampSeconds)
                true
            }
            mp.setOnPreparedListener { m ->
                m.setVolume(0f, 0f)
                m.start()
                ramp(m, gain, rampSeconds)
            }
            player = mp
            mp.prepareAsync()
        } catch (_: Exception) {
            mp.release()
            if (player === mp) player = null
            tryPlay(uris, i + 1, gain, rampSeconds)
        }
    }

    private fun ramp(mp: MediaPlayer, gain: Float, rampSeconds: Int) {
        val startedAt = System.currentTimeMillis()
        val rampMs = rampSeconds * 1000L
        val step = object : Runnable {
            override fun run() {
                if (player !== mp) return
                val f = if (rampMs <= 0) 1f else ((System.currentTimeMillis() - startedAt) / rampMs.toFloat()).coerceAtMost(1f)
                runCatching { mp.setVolume(gain * f, gain * f) }
                if (f < 1f) handler.postDelayed(this, 250)
            }
        }
        step.run()
    }

    /** Alarm volume 0 is the classic way to oversleep: raise it, then restore it afterwards. */
    private fun ensureAudible(settings: LadderSettings) {
        val am = getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val current = am.getStreamVolume(AudioManager.STREAM_ALARM)
        val wanted = (settings.minStreamPercent ?: if (current == 0) DEFAULT_RESCUE_PERCENT else 0) * max / 100
        if (wanted > current) {
            if (savedStreamVolume == null) savedStreamVolume = current
            am.setStreamVolume(AudioManager.STREAM_ALARM, wanted, 0)
        }
    }

    private fun requestFocus() {
        val am = getSystemService(AudioManager::class.java)
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
            )
            .setOnAudioFocusChangeListener { }
            .build()
        am.requestAudioFocus(req)
        focus = req
    }

    private fun vibrate() {
        val v = getSystemService(VibratorManager::class.java).defaultVibrator
        v.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0),
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
        )
    }

    private fun acquireWakeLock(settings: LadderSettings) {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "wakeladder:ring").apply {
            acquire((settings.ringTimeoutMinutes + 1) * 60_000L)
        }
    }

    private fun releaseAudio() {
        handler.removeCallbacksAndMessages(null)
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        runCatching { getSystemService(VibratorManager::class.java).defaultVibrator.cancel() }
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focus = null
        savedStreamVolume?.let {
            getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, it, 0)
        }
        savedStreamVolume = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    companion object {
        private const val DEFAULT_RESCUE_PERCENT = 40

        fun ring(context: Context, mode: String, index: Int) {
            context.startForegroundService(
                Intent(context, RingService::class.java)
                    .setAction(Actions.RING)
                    .putExtra(Actions.EXTRA_MODE, mode)
                    .putExtra(Actions.EXTRA_INDEX, index),
            )
        }

        fun send(context: Context, action: String, index: Int = -1) {
            context.startService(
                Intent(context, RingService::class.java).setAction(action).putExtra(Actions.EXTRA_INDEX, index),
            )
        }
    }
}
