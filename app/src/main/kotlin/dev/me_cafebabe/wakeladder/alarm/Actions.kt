package dev.me_cafebabe.wakeladder.alarm

object Actions {
    private const val P = "dev.me_cafebabe.wakeladder.action."
    const val RUNG = P + "RUNG"
    const val FOLLOW_UP = P + "FOLLOW_UP"
    const val NUDGE = P + "NUDGE"
    const val NUDGE_ACCEPT = P + "NUDGE_ACCEPT"

    // Sent to RingService
    const val RING = P + "RING"
    const val SILENCE = P + "SILENCE"
    const val CONFIRM_UP = P + "CONFIRM_UP"
    const val FOLLOW_UP_OK = P + "FOLLOW_UP_OK"

    const val EXTRA_INDEX = "index"
    const val EXTRA_MODE = "mode"
    const val EXTRA_TIME_MS = "time_ms"

    const val MODE_RUNG = "rung"
    const val MODE_FOLLOW_UP = "follow_up"
}
