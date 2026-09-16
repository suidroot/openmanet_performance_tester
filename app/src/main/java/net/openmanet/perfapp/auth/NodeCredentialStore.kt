package net.openmanet.perfapp.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers the username/password used to log into a node, keyed by IP address, so reconnecting
 * doesn't require retyping the real device credentials (openmanetd's login is PAM-backed against
 * the device's own admin account - see rpc/AuthRepository). Backed by EncryptedSharedPreferences
 * (AES-256, keys held in the Android Keystore) rather than plain Room/DataStore, since this is a
 * real login credential, not incidental app state, and the manifest has android:allowBackup="true"
 * - an unencrypted store would put both `.username`/`.password` values on the Cloud/local backup
 * one bad restore-to-attacker's-device away from being readable. Keystore-backed keys don't
 * survive a backup/restore to a different device, so a restored blob is just garbage there.
 */
@Singleton
class NodeCredentialStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "node_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun save(nodeIp: String, username: String, password: String) {
        prefs.edit()
            .putString(usernameKey(nodeIp), username)
            .putString(passwordKey(nodeIp), password)
            .apply()
    }

    /** The remembered username/password for this node, or null if nothing's saved yet. */
    fun get(nodeIp: String): Pair<String, String>? {
        val username = prefs.getString(usernameKey(nodeIp), null) ?: return null
        val password = prefs.getString(passwordKey(nodeIp), null) ?: return null
        return username to password
    }

    fun clear(nodeIp: String) {
        prefs.edit()
            .remove(usernameKey(nodeIp))
            .remove(passwordKey(nodeIp))
            .apply()
    }

    private fun usernameKey(nodeIp: String) = "$nodeIp.username"
    private fun passwordKey(nodeIp: String) = "$nodeIp.password"
}
