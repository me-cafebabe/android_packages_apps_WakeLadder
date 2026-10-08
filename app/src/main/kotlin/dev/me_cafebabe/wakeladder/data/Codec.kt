package dev.me_cafebabe.wakeladder.data

import dev.me_cafebabe.wakeladder.domain.DismissTask
import dev.me_cafebabe.wakeladder.domain.FollowUpState
import dev.me_cafebabe.wakeladder.domain.LadderPlan
import dev.me_cafebabe.wakeladder.domain.LadderSettings
import dev.me_cafebabe.wakeladder.domain.Rung
import dev.me_cafebabe.wakeladder.domain.RungOverride
import dev.me_cafebabe.wakeladder.domain.RungState
import dev.me_cafebabe.wakeladder.domain.Sound
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import org.json.JSONArray
import org.json.JSONObject

/** JSON (de)serialisation of settings and plan. Unknown/missing fields fall back to defaults. */
object Codec {

    fun encode(s: LadderSettings): String = JSONObject().apply {
        put("rungCount", s.rungCount)
        put("intervalMinutes", s.intervalMinutes)
        put("startVolume", s.startVolumePercent)
        put("endVolume", s.endVolumePercent)
        put("sound", s.defaultSound.uri ?: JSONObject.NULL)
        put("vibrate", s.vibrate)
        put("rampSeconds", s.rampSeconds)
        put("minStream", s.minStreamPercent ?: JSONObject.NULL)
        put("ringTimeout", s.ringTimeoutMinutes)
        put("task", s.dismissTask.name)
        put("taskLastOnly", s.taskOnLastRungOnly)
        put("followUp", s.followUpEnabled)
        put("followUpMinutes", s.followUpMinutes)
        put("powerOff", s.powerOffAlarm)
        put("fallback", s.fallbackEnabled)
        put("nudge", s.bedtimeNudgeEnabled)
        put("nudgeTime", s.bedtimeNudgeTime.toString())
        put("lastUsed", s.lastUsedTime.toString())
        put("overrides", JSONObject().also { o ->
            s.overrides.forEach { (i, r) ->
                o.put(i.toString(), JSONObject().apply {
                    put("volume", r.volumePercent ?: JSONObject.NULL)
                    if (r.sound != null) put("sound", r.sound.uri ?: "") // "" = explicit default
                    put("vibrate", r.vibrate ?: JSONObject.NULL)
                })
            }
        })
        put("weekdays", JSONObject().also { o ->
            s.weekdayDefaults.forEach { (d, t) -> o.put(d.name, t.toString()) }
        })
    }.toString()

    fun decodeSettings(json: String?): LadderSettings {
        if (json.isNullOrBlank()) return LadderSettings()
        return try {
            val o = JSONObject(json)
            val d = LadderSettings()
            LadderSettings(
                rungCount = o.optInt("rungCount", d.rungCount)
                    .coerceIn(LadderSettings.MIN_RUNGS, LadderSettings.MAX_RUNGS),
                intervalMinutes = o.optInt("intervalMinutes", d.intervalMinutes)
                    .coerceIn(LadderSettings.MIN_INTERVAL, LadderSettings.MAX_INTERVAL),
                startVolumePercent = o.optInt("startVolume", d.startVolumePercent),
                endVolumePercent = o.optInt("endVolume", d.endVolumePercent),
                defaultSound = Sound(o.optNullableString("sound")),
                vibrate = o.optBoolean("vibrate", d.vibrate),
                overrides = o.optJSONObject("overrides")?.let { ov ->
                    ov.keys().asSequence().associate { k ->
                        val r = ov.getJSONObject(k)
                        k.toInt() to RungOverride(
                            volumePercent = if (r.isNull("volume")) null else r.optInt("volume"),
                            sound = if (r.has("sound")) Sound(r.getString("sound").ifEmpty { null }) else null,
                            vibrate = if (r.isNull("vibrate")) null else r.optBoolean("vibrate"),
                        )
                    }
                } ?: emptyMap(),
                rampSeconds = o.optInt("rampSeconds", d.rampSeconds),
                minStreamPercent = if (o.isNull("minStream")) null else o.optInt("minStream"),
                ringTimeoutMinutes = o.optInt("ringTimeout", d.ringTimeoutMinutes),
                dismissTask = enumOr(o.optString("task"), DismissTask.NONE),
                taskOnLastRungOnly = o.optBoolean("taskLastOnly", d.taskOnLastRungOnly),
                followUpEnabled = o.optBoolean("followUp", d.followUpEnabled),
                followUpMinutes = o.optInt("followUpMinutes", d.followUpMinutes),
                powerOffAlarm = o.optBoolean("powerOff", d.powerOffAlarm),
                fallbackEnabled = o.optBoolean("fallback", d.fallbackEnabled),
                bedtimeNudgeEnabled = o.optBoolean("nudge", d.bedtimeNudgeEnabled),
                bedtimeNudgeTime = o.optTime("nudgeTime", d.bedtimeNudgeTime),
                weekdayDefaults = o.optJSONObject("weekdays")?.let { w ->
                    w.keys().asSequence().mapNotNull { k ->
                        val day = runCatching { DayOfWeek.valueOf(k) }.getOrNull()
                        val t = runCatching { LocalTime.parse(w.getString(k)) }.getOrNull()
                        if (day != null && t != null) day to t else null
                    }.toMap()
                } ?: emptyMap(),
                lastUsedTime = o.optTime("lastUsed", d.lastUsedTime),
            )
        } catch (_: Exception) {
            LadderSettings()
        }
    }

    fun encode(p: LadderPlan): String = JSONObject().apply {
        put("target", p.target.toString())
        put("rungs", JSONArray().also { a ->
            p.rungs.forEach { r ->
                a.put(JSONObject().put("i", r.index).put("t", r.timeMs).put("s", r.state.name))
            }
        })
        put("up", p.upConfirmedAtMs ?: JSONObject.NULL)
        put("fu", p.followUp.name)
        put("fuAt", p.followUpAtMs)
        put("fuCount", p.followUpCount)
        put("auto", p.auto)
    }.toString()

    fun decodePlan(json: String?): LadderPlan? {
        if (json.isNullOrBlank()) return null
        return try {
            val o = JSONObject(json)
            val a = o.getJSONArray("rungs")
            LadderPlan(
                target = LocalDateTime.parse(o.getString("target")),
                rungs = (0 until a.length()).map {
                    val r = a.getJSONObject(it)
                    Rung(r.getInt("i"), r.getLong("t"), enumOr(r.getString("s"), RungState.SILENCED))
                },
                upConfirmedAtMs = if (o.isNull("up")) null else o.getLong("up"),
                followUp = enumOr(o.optString("fu"), FollowUpState.NONE),
                followUpAtMs = o.optLong("fuAt"),
                followUpCount = o.optInt("fuCount"),
                auto = o.optBoolean("auto"),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun JSONObject.optNullableString(k: String): String? =
        if (isNull(k)) null else optString(k).ifEmpty { null }

    private fun JSONObject.optTime(k: String, def: LocalTime): LocalTime =
        runCatching { LocalTime.parse(getString(k)) }.getOrDefault(def)

    private inline fun <reified E : Enum<E>> enumOr(name: String, def: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: def
}
