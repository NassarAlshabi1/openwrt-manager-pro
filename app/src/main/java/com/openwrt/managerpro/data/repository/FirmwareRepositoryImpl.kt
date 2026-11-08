package com.openwrt.managerpro.data.repository

import com.openwrt.managerpro.data.rpc.UbusClient
import com.openwrt.managerpro.data.rpc.asObj
import com.openwrt.managerpro.data.rpc.obj
import com.openwrt.managerpro.data.rpc.str
import com.openwrt.managerpro.domain.FirmwareRepository
import com.openwrt.managerpro.domain.FirmwareStatus
import com.openwrt.managerpro.domain.VersionCompare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks the running firmware version against the official OpenWrt
 * sysupgrade server (https://sysupgrade.openwrt.org).
 */
@Singleton
class FirmwareRepositoryImpl @Inject constructor(
    private val ubus: UbusClient,
    private val okHttpClient: OkHttpClient,
) : FirmwareRepository {

    override suspend fun checkUpdate(): FirmwareStatus {
        val board = ubus.call("system", "board")
        val release = board.obj("release")
        val current = release.str("version")
        val target = release.str("target")
        val model = board.str("model")
        val latest = queryLatestVersion()
        val updateAvailable = current.isNotBlank() && !latest.isNullOrBlank() &&
            VersionCompare.isUpdateAvailable(current, latest)
        val url = if (!latest.isNullOrBlank()) {
            val t = URLEncoder.encode(target, "UTF-8")
            "https://firmware-selector.openwrt.org/?version=$latest&target=$t"
        } else {
            "https://firmware-selector.openwrt.org"
        }
        return FirmwareStatus(
            currentVersion = current,
            latestVersion = latest,
            updateAvailable = updateAvailable,
            target = target,
            model = model,
            releaseUrl = url,
            checkedAtEpochMs = System.currentTimeMillis(),
        )
    }

    private suspend fun queryLatestVersion(): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://sysupgrade.openwrt.org/api/v1/overview")
            .header("User-Agent", "OpenWrtManagerPro/1.0")
            .build()
        try {
            okHttpClient.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                val text = r.body?.string() ?: return@withContext null
                parseLatest(text)
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** Handles both {latest:{version:..}} and {latest:".."} shapes; falls back to releases[]. */
        fun parseLatest(text: String): String? {
            val root = try { json.parseToJsonElement(text) } catch (e: Exception) { return null }
            val rootObj = root as? JsonObject ?: return null
            val latest = rootObj["latest"]
            when (latest) {
                is JsonPrimitive -> latest.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
                is JsonObject -> latest["version"]?.let { v ->
                    (v as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
                }
                else -> Unit
            }
            val releases = rootObj["releases"] as? JsonArray ?: return null
            val candidates = releases.mapNotNull { el ->
                when (el) {
                    is JsonPrimitive -> el.contentOrNull
                    is JsonObject -> (el["version"] as? JsonPrimitive)?.contentOrNull
                    else -> null
                }
            }.filter { it.isNotBlank() }
            val stable = candidates.firstOrNull {
                !it.contains("rc", ignoreCase = true) && !it.contains("snapshot", ignoreCase = true)
            }
            return stable ?: candidates.firstOrNull()
        }
    }
}
