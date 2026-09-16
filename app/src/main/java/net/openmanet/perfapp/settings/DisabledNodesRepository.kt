package net.openmanet.perfapp.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.disabledNodesDataStore by preferencesDataStore(name = "disabled_nodes")

/**
 * Nodes the user has excluded from test sessions, keyed by base hostname (see rpc/Hostnames.kt -
 * the same clean, interface-suffix-stripped identity used everywhere else a node is named).
 * Persisted so the choice survives reconnects rather than resetting every session; SessionViewModel
 * filters these out when building a session's ping-target list.
 */
@Singleton
class DisabledNodesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val key = stringSetPreferencesKey("disabled_hostnames")

    val disabledHostnames: Flow<Set<String>> = context.disabledNodesDataStore.data
        .map { it[key].orEmpty() }

    suspend fun setDisabled(hostname: String, disabled: Boolean) {
        context.disabledNodesDataStore.edit { prefs ->
            val current = prefs[key].orEmpty().toMutableSet()
            if (disabled) current.add(hostname) else current.remove(hostname)
            prefs[key] = current
        }
    }
}
