package dev.me_cafebabe.wakeladder.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.me_cafebabe.wakeladder.domain.LadderPlan
import dev.me_cafebabe.wakeladder.domain.LadderSettings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Settings and the active plan, kept in device-protected storage so that alarms can be
 * rescheduled and rung before the first unlock after a reboot.
 */
class AppStore(context: Context) {
    private val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    ) {
        val dp = context.createDeviceProtectedStorageContext()
        File(dp.filesDir, "datastore/wakeladder.preferences_pb")
    }

    val settings: Flow<LadderSettings> = store.data.map { Codec.decodeSettings(it[SETTINGS]) }
    val plan: Flow<LadderPlan?> = store.data.map { Codec.decodePlan(it[PLAN]) }

    suspend fun settingsNow(): LadderSettings = settings.first()
    suspend fun planNow(): LadderPlan? = plan.first()

    suspend fun updateSettings(f: (LadderSettings) -> LadderSettings) {
        store.edit { it[SETTINGS] = Codec.encode(f(Codec.decodeSettings(it[SETTINGS]))) }
    }

    /**
     * Atomically transforms the plan. Returning null clears it. The new value is returned so
     * callers can reschedule from exactly what was stored.
     */
    suspend fun updatePlan(f: (LadderPlan?) -> LadderPlan?): LadderPlan? {
        var result: LadderPlan? = null
        store.edit { prefs ->
            result = f(Codec.decodePlan(prefs[PLAN]))
            val r = result
            if (r == null) prefs.remove(PLAN) else prefs[PLAN] = Codec.encode(r)
        }
        return result
    }

    /** Power-off alarm time currently registered with the service (to cancel on change). */
    suspend fun poweroffRegistered(): Long? = store.data.first()[POWEROFF]?.toLongOrNull()

    suspend fun setPoweroffRegistered(ms: Long?) {
        store.edit { if (ms == null) it.remove(POWEROFF) else it[POWEROFF] = ms.toString() }
    }

    private companion object {
        val SETTINGS = stringPreferencesKey("settings")
        val PLAN = stringPreferencesKey("plan")
        val POWEROFF = stringPreferencesKey("poweroff_ms")
    }
}
