package com.openwrt.managerpro.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.openwrt.managerpro.domain.RouterProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/** Single DataStore instance for the whole app (declared exactly once). */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "openwrt_prefs")

private object Keys {
    val BASE_URL = stringPreferencesKey("router_base_url")
    val USERNAME = stringPreferencesKey("router_username")
    val MONITORING = booleanPreferencesKey("monitoring_enabled")
    val NOTIFY_NEW_DEVICE = booleanPreferencesKey("notify_new_device")
    val NOTIFY_OFFLINE = booleanPreferencesKey("notify_offline")
    val NOTIFY_FIRMWARE = booleanPreferencesKey("notify_firmware")
    val MONITOR_INTERVAL_MIN = intPreferencesKey("monitor_interval_min")
    val KNOWN_MACS = stringPreferencesKey("known_macs")
}

/** Router identity + credentials (password kept in EncryptedSharedPreferences). */
@Singleton
class ProfileStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var cached: RouterProfile? = null

    init {
        scope.launch { cached = readProfile() }
    }

    suspend fun profile(): RouterProfile? {
        cached?.let { return it }
        val p = readProfile()
        cached = p
        return p
    }

    /** Fast path for code that already triggered a suspend profile() before. */
    fun cachedProfile(): RouterProfile? = cached ?: runBlocking { profile() }

    suspend fun save(profile: RouterProfile) {
        cached = profile
        context.dataStore.edit { prefs ->
            prefs[Keys.BASE_URL] = profile.baseUrl
            prefs[Keys.USERNAME] = profile.username
        }
        securePrefs().edit().putString("router_password", profile.password).apply()
    }

    suspend fun clear() {
        cached = null
        context.dataStore.edit { it.clear() }
        securePrefs().edit().clear().apply()
    }

    private suspend fun readProfile(): RouterProfile? {
        val prefs = context.dataStore.data.first()
        val url = prefs[Keys.BASE_URL] ?: return null
        val user = prefs[Keys.USERNAME] ?: "root"
        val pass = securePrefs().getString("router_password") ?: ""
        return RouterProfile(url, user, pass)
    }

    private fun securePrefs() = EncryptedSharedPreferences.create(
        context,
        "openwrt_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
}

/** User-facing settings. */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val monitoringEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.MONITORING] ?: true }
    val notifyNewDevice: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.NOTIFY_NEW_DEVICE] ?: true }
    val notifyOffline: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.NOTIFY_OFFLINE] ?: true }
    val notifyFirmware: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.NOTIFY_FIRMWARE] ?: true }
    val monitorIntervalMin: Flow<Int> =
        context.dataStore.data.map { it[Keys.MONITOR_INTERVAL_MIN] ?: 15 }

    suspend fun setMonitoringEnabled(v: Boolean) = context.dataStore.edit { it[Keys.MONITORING] = v }
    suspend fun setNotifyNewDevice(v: Boolean) = context.dataStore.edit { it[Keys.NOTIFY_NEW_DEVICE] = v }
    suspend fun setNotifyOffline(v: Boolean) = context.dataStore.edit { it[Keys.NOTIFY_OFFLINE] = v }
    suspend fun setNotifyFirmware(v: Boolean) = context.dataStore.edit { it[Keys.NOTIFY_FIRMWARE] = v }
    suspend fun setMonitorIntervalMin(v: Int) = context.dataStore.edit { it[Keys.MONITOR_INTERVAL_MIN] = v }
}

/** Persistent per-device first/last seen and last-known MAC set. */
@Singleton
class DeviceStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun seenMap(): Map<String, Pair<Long, Long>> =
        context.dataStore.data.first().asMap()
            .filterKeys { it.name.startsWith("dev_") }
            .mapKeys { it.key.name.removePrefix("dev_").replace('_', ':') }
            .mapValues { entry ->
                val v = entry.value as? String ?: "0:0"
                val parts = v.split(":")
                (parts.getOrNull(0)?.toLongOrNull() ?: 0L) to (parts.getOrNull(1)?.toLongOrNull() ?: 0L)
            }

    suspend fun recordSeen(macs: Set<String>, nowMs: Long) {
        if (macs.isEmpty()) return
        context.dataStore.edit { prefs ->
            for (mac in macs) {
                val key = stringPreferencesKey("dev_${mac.replace(':', '_')}")
                val old = prefs[key]?.split(":")
                val first = old?.getOrNull(0)?.toLongOrNull() ?: nowMs
                prefs[key] = "$first:$nowMs"
            }
        }
    }

    suspend fun knownMacs(): Set<String> =
        context.dataStore.data.first()[Keys.KNOWN_MACS]
            ?.split(",")?.filter { it.isNotBlank() }?.toSet()
            ?: emptySet()

    suspend fun updateKnownMacs(macs: Set<String>) {
        context.dataStore.edit { it[Keys.KNOWN_MACS] = macs.sorted().joinToString(",") }
    }
}
