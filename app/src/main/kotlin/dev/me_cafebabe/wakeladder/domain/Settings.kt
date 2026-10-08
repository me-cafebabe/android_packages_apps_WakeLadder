package dev.me_cafebabe.wakeladder.domain

import java.time.DayOfWeek
import java.time.LocalTime

enum class DismissTask { NONE, MATH, SHAKE, STEPS }

/** A ringtone choice: [uri] null means "the system default alarm sound". */
data class Sound(val uri: String? = null)

/** Optional per-rung overrides; null fields fall back to the global defaults. */
data class RungOverride(
    val volumePercent: Int? = null,
    val sound: Sound? = null,
    val vibrate: Boolean? = null,
)

/** Fully resolved look of one rung. */
data class RungStyle(val volumePercent: Int, val sound: Sound, val vibrate: Boolean)

data class LadderSettings(
    val rungCount: Int = 4,
    val intervalMinutes: Int = 20,
    /** Volume of the first rung; later rungs interpolate linearly up to [endVolumePercent]. */
    val startVolumePercent: Int = 30,
    val endVolumePercent: Int = 100,
    val defaultSound: Sound = Sound(),
    /** Vibration is optional and off by default. */
    val vibrate: Boolean = false,
    /** Per rung index (0 = earliest) overrides. */
    val overrides: Map<Int, RungOverride> = emptyMap(),
    /** Seconds over which a rung fades from silence to its volume; 0 = no fade. */
    val rampSeconds: Int = 20,
    /** If set, the alarm stream is raised to at least this percentage while ringing. */
    val minStreamPercent: Int? = null,
    /** Auto-silence a rung that nobody touched after this many minutes. */
    val ringTimeoutMinutes: Int = 5,
    val dismissTask: DismissTask = DismissTask.NONE,
    /** Apply the dismissal task only to the last rung (confirming earlier ones is always cheap). */
    val taskOnLastRungOnly: Boolean = true,
    val followUpEnabled: Boolean = true,
    val followUpMinutes: Int = 7,
    val powerOffAlarm: Boolean = true,
    /** If no wake time was set by the bedtime time, set one from the weekday default. */
    val fallbackEnabled: Boolean = true,
    val bedtimeNudgeEnabled: Boolean = true,
    val bedtimeNudgeTime: LocalTime = LocalTime.of(22, 30),
    /** Default wake time per weekday; absent = no default. */
    val weekdayDefaults: Map<DayOfWeek, LocalTime> = emptyMap(),
    /** Used when the user has not set tomorrow's time. */
    val lastUsedTime: LocalTime = LocalTime.of(7, 0),
) {
    init {
        require(rungCount in MIN_RUNGS..MAX_RUNGS) { "rungCount" }
        require(intervalMinutes in MIN_INTERVAL..MAX_INTERVAL) { "intervalMinutes" }
    }

    fun styleFor(index: Int): RungStyle {
        val o = overrides[index]
        val t = if (rungCount <= 1) 1f else index.coerceIn(0, rungCount - 1).toFloat() / (rungCount - 1)
        val auto = (startVolumePercent + (endVolumePercent - startVolumePercent) * t).toInt()
        return RungStyle(
            volumePercent = (o?.volumePercent ?: auto).coerceIn(0, 100),
            sound = o?.sound ?: defaultSound,
            vibrate = o?.vibrate ?: vibrate,
        )
    }

    /** Task required to dismiss rung [index]; earlier rungs only ever need a plain confirmation. */
    fun taskFor(index: Int): DismissTask =
        if (!taskOnLastRungOnly || index == rungCount - 1) dismissTask else DismissTask.NONE

    companion object {
        const val MIN_RUNGS = 1
        const val MAX_RUNGS = 6
        const val MIN_INTERVAL = 5
        const val MAX_INTERVAL = 30

        val GENTLE = LadderSettings()
        fun preset(rungs: Int, interval: Int, base: LadderSettings) =
            base.copy(rungCount = rungs, intervalMinutes = interval)
    }
}
