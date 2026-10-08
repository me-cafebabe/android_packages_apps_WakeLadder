package dev.me_cafebabe.wakeladder.data

import dev.me_cafebabe.wakeladder.domain.DismissTask
import dev.me_cafebabe.wakeladder.domain.LadderPlanner
import dev.me_cafebabe.wakeladder.domain.LadderSettings
import dev.me_cafebabe.wakeladder.domain.PlanReducer
import dev.me_cafebabe.wakeladder.domain.RungOverride
import dev.me_cafebabe.wakeladder.domain.Sound
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodecTest {
    @Test
    fun settingsRoundTrip() {
        val s = LadderSettings(
            rungCount = 3, intervalMinutes = 15, vibrate = true,
            defaultSound = Sound("content://media/1"),
            overrides = mapOf(
                0 to RungOverride(volumePercent = 20, vibrate = false),
                2 to RungOverride(sound = Sound(null)),
            ),
            minStreamPercent = 60, dismissTask = DismissTask.SHAKE,
            weekdayDefaults = mapOf(DayOfWeek.MONDAY to LocalTime.of(6, 30)),
            bedtimeNudgeTime = LocalTime.of(23, 0), fallbackEnabled = false,
        )
        assertEquals(s, Codec.decodeSettings(Codec.encode(s)))
    }

    @Test
    fun garbageOrEmptyFallsBackToDefaults() {
        assertEquals(LadderSettings(), Codec.decodeSettings(null))
        assertEquals(LadderSettings(), Codec.decodeSettings("{not json"))
        assertNull(Codec.decodePlan("nope"))
    }

    @Test
    fun outOfRangeValuesAreClamped() {
        val s = Codec.decodeSettings("""{"rungCount":99,"intervalMinutes":1}""")
        assertEquals(LadderSettings.MAX_RUNGS, s.rungCount)
        assertEquals(LadderSettings.MIN_INTERVAL, s.intervalMinutes)
    }

    @Test
    fun planRoundTrip() {
        val z = ZoneId.of("UTC")
        var p = LadderPlanner.plan(LocalDateTime.of(2026, 10, 10, 6, 40), LadderSettings(), z, 0)
        p = PlanReducer.confirmUp(PlanReducer.onRungFired(p, 1), LadderSettings(), 12345).copy(auto = true)
        assertEquals(p, Codec.decodePlan(Codec.encode(p)))
    }
}
