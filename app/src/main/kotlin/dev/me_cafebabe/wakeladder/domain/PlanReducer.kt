package dev.me_cafebabe.wakeladder.domain

/**
 * Pure state transitions for a [LadderPlan]. Every function returns a new plan.
 *
 * Invariant: silencing a rung never cancels another rung. Only [confirmUp] and [cancelAll] do.
 */
object PlanReducer {

    /** The alarm for rung [index] fired. */
    fun onRungFired(plan: LadderPlan, index: Int): LadderPlan =
        plan.mapRung(index) { if (it.state == RungState.SCHEDULED) it.copy(state = RungState.RINGING) else it }

    /** The user (or a timeout) silenced rung [index]; later rungs stay scheduled. */
    fun silence(plan: LadderPlan, index: Int): LadderPlan =
        plan.mapRung(index) { if (it.state == RungState.RINGING) it.copy(state = RungState.SILENCED) else it }

    /**
     * The user confirmed being up: silence what rings, cancel everything still scheduled and,
     * if enabled, schedule the "still up?" re-check.
     */
    fun confirmUp(plan: LadderPlan, settings: LadderSettings, nowMs: Long): LadderPlan {
        val rungs = plan.rungs.map {
            when (it.state) {
                RungState.SCHEDULED -> it.copy(state = RungState.CANCELLED)
                RungState.RINGING -> it.copy(state = RungState.SILENCED)
                else -> it
            }
        }
        val followUp = settings.followUpEnabled && plan.followUp != FollowUpState.RINGING
        return plan.copy(
            rungs = rungs,
            upConfirmedAtMs = nowMs,
            followUp = if (followUp) FollowUpState.SCHEDULED else FollowUpState.NONE,
            followUpAtMs = nowMs + settings.followUpMinutes * 60_000L,
            followUpCount = 0,
        )
    }

    /**
     * Nobody touched the last rung before it timed out: the user is probably still asleep, so
     * instead of going quiet, nag again soon via the follow-up mechanism (if enabled).
     */
    fun lastRungTimedOut(plan: LadderPlan, settings: LadderSettings, nowMs: Long): LadderPlan {
        val silenced = silence(plan, plan.lastRung.index)
        if (!settings.followUpEnabled) return silenced
        return silenced.copy(
            followUp = FollowUpState.SCHEDULED,
            followUpAtMs = nowMs + NAG_DELAY_MS,
            followUpCount = 0,
        )
    }

    fun cancelAll(plan: LadderPlan): LadderPlan = plan.copy(
        rungs = plan.rungs.map {
            if (it.state == RungState.SCHEDULED || it.state == RungState.RINGING)
                it.copy(state = RungState.CANCELLED) else it
        },
        followUp = FollowUpState.NONE,
    )

    fun onFollowUpFired(plan: LadderPlan): LadderPlan =
        if (plan.followUp == FollowUpState.SCHEDULED) plan.copy(followUp = FollowUpState.RINGING) else plan

    /** The user answered "yes, I'm still up". */
    fun followUpAcknowledged(plan: LadderPlan): LadderPlan = plan.copy(followUp = FollowUpState.NONE)

    /**
     * Nobody answered the re-check. Retry up to [MAX_FOLLOW_UPS] times; after that, give up
     * (the user was already woken by the ladder at least once).
     */
    fun followUpTimedOut(plan: LadderPlan, settings: LadderSettings, nowMs: Long): LadderPlan {
        val count = plan.followUpCount + 1
        return if (count >= MAX_FOLLOW_UPS) {
            plan.copy(followUp = FollowUpState.NONE, followUpCount = count)
        } else {
            plan.copy(
                followUp = FollowUpState.SCHEDULED,
                followUpAtMs = nowMs + settings.followUpMinutes * 60_000L,
                followUpCount = count,
            )
        }
    }

    /**
     * After a reboot or process death: a rung whose time passed without being handled is
     * treated as silenced (it was missed) so the plan can move on; the next one still fires.
     */
    fun settleMissed(plan: LadderPlan, nowMs: Long, graceMs: Long): LadderPlan = plan.copy(
        rungs = plan.rungs.map {
            if (it.state == RungState.SCHEDULED && it.timeMs + graceMs < nowMs)
                it.copy(state = RungState.SILENCED) else it
        },
    )

    const val MAX_FOLLOW_UPS = 3
    const val NAG_DELAY_MS = 2 * 60_000L

    private fun LadderPlan.mapRung(index: Int, f: (Rung) -> Rung): LadderPlan =
        copy(rungs = rungs.map { if (it.index == index) f(it) else it })
}
