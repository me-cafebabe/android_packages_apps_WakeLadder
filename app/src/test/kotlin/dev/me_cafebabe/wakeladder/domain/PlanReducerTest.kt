package dev.me_cafebabe.wakeladder.domain

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanReducerTest {
    private val zone = ZoneId.of("UTC")
    private val settings = LadderSettings()
    private val t0 = LocalDateTime.of(2026, 10, 10, 6, 40).atZone(zone).toInstant().toEpochMilli()
    private val fresh = LadderPlanner.plan(LocalDateTime.of(2026, 10, 10, 6, 40), settings, zone, 0)

    private fun states(p: LadderPlan) = p.rungs.map { it.state }

    @Test
    fun silencingAnEarlyRungKeepsTheLadderGoing() {
        val p = PlanReducer.silence(PlanReducer.onRungFired(fresh, 0), 0)
        assertEquals(
            listOf(RungState.SILENCED, RungState.SCHEDULED, RungState.SCHEDULED, RungState.SCHEDULED),
            states(p),
        )
        assertEquals(1, p.nextRung()!!.index)
    }

    @Test
    fun confirmUpCancelsRestAndSchedulesFollowUp() {
        val ringing = PlanReducer.onRungFired(fresh, 1)
        val p = PlanReducer.confirmUp(ringing, settings, nowMs = t0 - 1000)
        assertEquals(
            listOf(RungState.CANCELLED, RungState.SILENCED, RungState.CANCELLED, RungState.CANCELLED),
            states(p),
        )
        assertEquals(FollowUpState.SCHEDULED, p.followUp)
        assertEquals(t0 - 1000 + 7 * 60_000L, p.nextDueMs())
    }

    @Test
    fun noFollowUpWhenDisabled() {
        val p = PlanReducer.confirmUp(fresh, settings.copy(followUpEnabled = false), 1)
        assertEquals(FollowUpState.NONE, p.followUp)
        assertEquals(true, p.isFinished)
    }

    @Test
    fun followUpRetriesThenGivesUp() {
        var p = PlanReducer.confirmUp(fresh, settings, 0)
        repeat(PlanReducer.MAX_FOLLOW_UPS - 1) {
            p = PlanReducer.followUpTimedOut(PlanReducer.onFollowUpFired(p), settings, 1000)
            assertEquals(FollowUpState.SCHEDULED, p.followUp)
        }
        p = PlanReducer.followUpTimedOut(PlanReducer.onFollowUpFired(p), settings, 1000)
        assertEquals(FollowUpState.NONE, p.followUp)
        assertEquals(true, p.isFinished)
    }

    @Test
    fun acknowledgedFollowUpFinishesPlan() {
        val p = PlanReducer.followUpAcknowledged(PlanReducer.onFollowUpFired(PlanReducer.confirmUp(fresh, settings, 0)))
        assertEquals(true, p.isFinished)
    }

    @Test
    fun settleMissedDropsStaleScheduledRungsOnly() {
        val now = fresh.rungs[1].timeMs + 10 * 60_000L // 10 min after rung 1
        val p = PlanReducer.settleMissed(fresh, now, graceMs = 5 * 60_000L)
        assertEquals(
            listOf(RungState.SILENCED, RungState.SILENCED, RungState.SCHEDULED, RungState.SCHEDULED),
            states(p),
        )
    }

    @Test
    fun firingTwiceOrSilencingIdleRungIsHarmless() {
        val a = PlanReducer.silence(fresh, 2) // not ringing
        assertEquals(fresh, a)
        val b = PlanReducer.onRungFired(PlanReducer.silence(PlanReducer.onRungFired(fresh, 0), 0), 0)
        assertEquals(RungState.SILENCED, b.rungs[0].state)
    }

    @Test
    fun cancelAllEndsEverything() {
        val p = PlanReducer.cancelAll(PlanReducer.onRungFired(fresh, 0))
        assertEquals(true, p.isFinished)
    }
}
