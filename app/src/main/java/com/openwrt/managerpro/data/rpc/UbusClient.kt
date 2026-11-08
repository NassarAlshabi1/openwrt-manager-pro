package com.openwrt.managerpro.data.rpc

import com.openwrt.managerpro.data.prefs.ProfileStore
import com.openwrt.managerpro.domain.RouterProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JSON-RPC client for OpenWrt ubus over HTTP (uhttpd-mod-ubus).
 * Endpoint: POST {baseUrl}/ubus/
 */
@Singleton
class UbusClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val profileStore: ProfileStore,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val idCounter = AtomicInteger(1)
    private val mutex = Mutex()

    @Volatile
    private var session: String = EMPTY_SESSION

    @Volatile
    private var baseUrl: String? = null

    /** Explicit login (used by onboarding / reconnect). Does not persist the profile. */
    suspend fun login(profile: RouterProfile): String = mutex.withLock {
        baseUrl = profile.baseUrl
        val token = doLogin(profile)
        session = token
        token
    }

    suspend fun invalidate() {
        session = EMPTY_SESSION
    }

    suspend fun ensureSession(): String {
        if (session != EMPTY_SESSION) return session
        val profile = profileStore.profile() ?: throw NotConnectedException()
        return mutex.withLock {
            if (session == EMPTY_SESSION) {
                baseUrl = profile.baseUrl
                session = doLogin(profile)
            }
            session
        }
    }

    /** Perform a ubus call and return the payload object (empty if absent). */
    suspend fun call(objectName: String, method: String, args: JsonObject = JsonObject(emptyMap())): JsonObject {
        var token = ensureSession()
        var resp = rawCall(token, objectName, method, args)
        if (resp.status == UbusCodes.PERMISSION_DENIED) {
            invalidate()
            token = ensureSession()
            resp = rawCall(token, objectName, method, args)
        }
        if (resp.status != UbusCodes.OK) throw UbusException(resp.status, "call $objectName.$method")
        return resp.payload ?: JsonObject(emptyMap())
    }

    suspend fun fileRead(path: String): String? {
        val result = try {
            call("file", "read", buildJsonObject { put("path", path) })
        } catch (e: UbusException) {
            if (e.code == UbusCodes.NOT_FOUND || e.code == UbusCodes.NO_DATA) return null else throw e
        }
        return result.strOrNull("data")?.takeIf { it.isNotEmpty() }
    }

    suspend fun fileExec(command: String, args: List<String> = emptyList()): ExecResult {
        val result = call("file", "exec", buildJsonObject {
            put("command", command)
            put("params", buildJsonArray { args.forEach { add(JsonPrimitive(it)) } })
        })
        return ExecResult(
            code = result.int("code"),
            stdout = result.str("stdout"),
            stderr = result.str("stderr"),
        )
    }

    private suspend fun doLogin(profile: RouterProfile): String {
        val resp = try {
            rawCall(EMPTY_SESSION, "session", "login", buildJsonObject {
                put("username", profile.username)
                put("password", profile.password)
            })
        } catch (e: RpcTransportException) {
            throw e
        } catch (e: IOException) {
            throw RpcTransportException(e)
        }
        if (resp.status != UbusCodes.OK) throw UbusException(resp.status, "login failed")
        val payload = resp.payload ?: throw UbusException(UbusCodes.UNKNOWN, "login: empty result")
        val token = UbusProtocol.extractSession(payload)
        if (token.isEmpty()) throw UbusException(UbusCodes.UNKNOWN, "login: no session token")
        return token
    }

    private suspend fun rawCall(token: String, objectName: String, method: String, args: JsonObject): UbusRawResponse =
        withContext(Dispatchers.IO) {
            val url = (baseUrl ?: throw NotConnectedException()).trimEnd('/') + "/ubus/"
            val body = UbusProtocol.buildRequest(idCounter.getAndIncrement().toLong(), token, objectName, method, args)
                .toString()
                .toRequestBody(jsonType)
            val request = Request.Builder().url(url).post(body).build()
            val response = try {
                okHttpClient.newCall(request).execute()
            } catch (e: IOException) {
                throw RpcTransportException(e)
            }
            response.use { r ->
                if (!r.isSuccessful) throw RpcTransportException(IOException("HTTP ${r.code}"))
                val text = r.body?.string() ?: throw RpcTransportException(IOException("empty body"))
                UbusProtocol.parseResponse(text)
            }
        }
}
