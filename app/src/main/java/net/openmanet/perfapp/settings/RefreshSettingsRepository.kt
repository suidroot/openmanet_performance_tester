package net.openmanet.perfapp.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.refreshSettingsDataStore by preferencesDataStore(name = "refresh_settings")

/** Persists the dashboard auto-refresh interval, configurable in Settings (default 2s). */
@Singleton
class RefreshSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyIntervalMs = longPreferencesKey("dashboard_refresh_interval_ms")

    val intervalMs: Flow<Long> = context.refreshSettingsDataStore.data
        .map { it[keyIntervalMs] ?: DEFAULT_INTERVAL_MS }

    suspend fun setIntervalMs(intervalMs: Long) {
        context.refreshSettingsDataStore.edit { it[keyIntervalMs] = intervalMs.coerceAtLeast(MIN_INTERVAL_MS) }
    }

    companion object {
        const val DEFAULT_INTERVAL_MS = 2_000L
        const val MIN_INTERVAL_MS = 500L
    }
}
