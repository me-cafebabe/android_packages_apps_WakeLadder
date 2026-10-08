package dev.me_cafebabe.wakeladder.domain

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

enum class RungState {
    /** Waiting for its time. */
    SCHEDULED,
    RINGING,
    /** Rang and was silenced (by the user or by timeout). */
    SILENCED,
    /** Cancelled because the user confirmed being up, or the ladder was cancelled. */
    CANCELLED,
}

data class Rung(val index: Int, val timeMs: Long, val state: RungState)

/** What is currently happening with the "still up?" re-check. */
enum class FollowUpState { NONE, SCHEDULED, RINGING }

/**
 * The one active ladder. [target] is wall-clock so it survives time zone changes;
 * rung instants are derived from it.
 */
data class LadderPlan(
    val target: LocalDateTime,
    val rungs: List<Rung>,
    val upConfirmedAtMs: Long? = null,
    val followUp: FollowUpState = FollowUpState.NONE,
    val followUpAtMs: Long = 0,
    val followUpCount: Int = 0,
    /** Created by the fallback rather than by the user. */
    val auto: Boolean = false,
) {
    val lastRung: Rung get() = rungs.last()

    /** Earliest rung still scheduled. */
    fun nextRung(): Rung? = rungs.filter { it.state == RungState.SCHEDULED }.minByOrNull { it.timeMs }

    fun ringingRung(): Rung? = rungs.firstOrNull { it.state == RungState.RINGING }

    /** Next instant anything is due (rung or follow-up), or null if the plan is finished. */
    fun nextDueMs(): Long? {
        val rung = nextRung()?.timeMs
        val fu = if (followUp == FollowUpState.SCHEDULED) followUpAtMs else null
        return listOfNotNull(rung, fu).minOrNull()
    }

    val isFinished: Boolean
        get() = nextDueMs() == null && ringingRung() == null && followUp != FollowUpState.RINGING
}

object LadderPlanner {

    /** Rung instants for [target], earliest first. Rungs already in the past (<= [nowMs]) are dropped. */
    fun plan(
        target: LocalDateTime,
        settings: LadderSettings,
        zone: ZoneId,
        nowMs: Long,
    ): LadderPlan {
        val targetMs = target.atZone(zone).toInstant().toEpochMilli()
        val step = settings.intervalMinutes * 60_000L
        val all = (0 until settings.rungCount).map { i ->
            val fromEnd = settings.rungCount - 1 - i
            targetMs - fromEnd * step
        }
        // Keep indices stable relative to the ladder so styles still line up with position.
        val rungs = all.mapIndexedNotNull { i, t ->
            if (t > nowMs) Rung(i, t, RungState.SCHEDULED) else null
        }
        return LadderPlan(target, rungs)
    }

    /**
     * Next occurrence of [time] strictly after [now]; the usual "tomorrow morning" answer.
     * If it is already past [time] today, that is tomorrow; if it is still before, it is today.
     */
    fun nextOccurrence(time: java.time.LocalTime, now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate().atTime(time)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    /**
     * Re-derives rung instants after a zone/time change, preserving the state of rungs by index.
     * Rungs now in the past that were still SCHEDULED are dropped.
     */
    fun replan(old: LadderPlan, settings: LadderSettings, zone: ZoneId, nowMs: Long): LadderPlan {
        val fresh = plan(old.target, settings, zone, nowMs)
        val byIndex = old.rungs.associateBy { it.index }
        val merged = fresh.rungs.map { r ->
            val prev = byIndex[r.index]
            if (prev != null && prev.state != RungState.SCHEDULED) r.copy(state = prev.state) else r
        }
        return old.copy(rungs = merged)
    }

    fun instant(ms: Long): Instant = Instant.ofEpochMilli(ms)
}
