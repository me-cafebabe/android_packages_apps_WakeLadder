package dev.me_cafebabe.wakeladder.domain

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LadderPlannerTest {
    private val zone = ZoneId.of("Asia/Singapore")
    private val settings = LadderSettings() // 4 rungs, 20 min
    private val target = LocalDateTime.of(2026, 10, 10, 6, 40)

    private fun ms(h: Int, m: Int, day: Int = 10) =
        LocalDateTime.of(2026, 10, day, h, m).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun ladderEndsAtTargetWithTwentyMinuteSteps() {
        val plan = LadderPlanner.plan(target, settings, zone, nowMs = ms(23, 0, 9))
        assertEquals(listOf(ms(5, 40), ms(6, 0), ms(6, 20), ms(6, 40)), plan.rungs.map { it.timeMs })
        assertEquals(listOf(0, 1, 2, 3), plan.rungs.map { it.index })
    }

    @Test
    fun pastRungsAreDroppedButIndicesStay() {
        val plan = LadderPlanner.plan(target, settings, zone, nowMs = ms(6, 5))
        assertEquals(listOf(2, 3), plan.rungs.map { it.index })
        assertEquals(ms(6, 20), plan.nextRung()!!.timeMs)
    }

    @Test
    fun singleRungLadderIsJustTheTarget() {
        val plan = LadderPlanner.plan(target, settings.copy(rungCount = 1), zone, ms(23, 0, 9))
        assertEquals(listOf(ms(6, 40)), plan.rungs.map { it.timeMs })
    }

    @Test
    fun targetAlreadyPastGivesEmptyPlan() {
        val plan = LadderPlanner.plan(target, settings, zone, nowMs = ms(7, 0))
        assertNull(plan.nextRung())
        assertEquals(true, plan.isFinished)
    }

    @Test
    fun nextOccurrenceRollsToTomorrowOnlyWhenPast() {
        val now = LocalDateTime.of(2026, 10, 9, 23, 0)
        assertEquals(LocalDateTime.of(2026, 10, 10, 6, 40), LadderPlanner.nextOccurrence(LocalTime.of(6, 40), now))
        assertEquals(LocalDateTime.of(2026, 10, 9, 23, 30), LadderPlanner.nextOccurrence(LocalTime.of(23, 30), now))
    }

    @Test
    fun replanKeepsStatesWhenZoneChanges() {
        val plan = LadderPlanner.plan(target, settings, zone, ms(23, 0, 9))
        val fired = PlanReducer.silence(PlanReducer.onRungFired(plan, 0), 0)
        val tokyo = ZoneId.of("Asia/Tokyo")
        val re = LadderPlanner.replan(fired, settings, tokyo, ms(23, 0, 9))
        assertEquals(RungState.SILENCED, re.rungs[0].state)
        // Same wall clock in a zone one hour ahead is one hour earlier in absolute time.
        assertEquals(plan.rungs[1].timeMs - 3_600_000L, re.rungs[1].timeMs)
    }

    @Test
    fun volumesEscalateAndOverridesWin() {
        val vols = (0..3).map { settings.styleFor(it).volumePercent }
        assertEquals(listOf(30, 53, 76, 100), vols)
        val s = settings.copy(overrides = mapOf(1 to RungOverride(volumePercent = 10, vibrate = true)))
        assertEquals(10, s.styleFor(1).volumePercent)
        assertEquals(true, s.styleFor(1).vibrate)
        assertEquals(false, s.styleFor(2).vibrate)
    }

    @Test
    fun taskAppliesToLastRungOnlyByDefault() {
        val s = settings.copy(dismissTask = DismissTask.MATH)
        assertEquals(DismissTask.NONE, s.taskFor(0))
        assertEquals(DismissTask.MATH, s.taskFor(3))
        assertEquals(DismissTask.MATH, s.copy(taskOnLastRungOnly = false).taskFor(0))
        assertEquals(DismissTask.NONE, settings.taskFor(3))
    }
}
