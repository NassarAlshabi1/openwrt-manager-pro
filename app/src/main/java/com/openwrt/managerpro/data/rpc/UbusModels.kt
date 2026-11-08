package com.openwrt.managerpro.data.rpc

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

// ---------- Exceptions ----------

class UbusException(val code: Int, message: String) : Exception("ubus error $code: $message")

class RpcTransportException(cause: Throwable) : Exception("cannot reach router: ${cause.message}", cause)

class NotConnectedException : Exception("no router profile configured")

// ---------- ubus status codes ----------

object UbusCodes {
    const val OK = 0
    const val INVALID_COMMAND = 1
    const val INVALID_ARGUMENT = 2
    const val METHOD_NOT_FOUND = 3
    const val NOT_FOUND = 4
    const val NO_DATA = 5
    const val PERMISSION_DENIED = 6
    const val TIMEOUT = 7
    const val NOT_SUPPORTED = 8
    const val UNKNOWN = 9
    const val CONNECTION_FAILED = 10
}

const val EMPTY_SESSION = "00000000000000000000000000000000"

// ---------- JSON helpers ----------

fun JsonElement?.asObj(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())

fun JsonObject.str(key: String, default: String = ""): String =
    (this[key] as? JsonPrimitive)?.contentOrNull ?: default

fun JsonObject.strOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

fun JsonObject.int(key: String, default: Int = 0): Int = intOrNull(key) ?: default

fun JsonObject.intOrNull(key: String): Int? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

fun JsonObject.long(key: String, default: Long = 0L): Long = longOrNull(key) ?: default

fun JsonObject.longOrNull(key: String): Long? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

fun JsonObject.double(key: String, default: Double = 0.0): Double = doubleOrNull(key) ?: default

fun JsonObject.doubleOrNull(key: String): Double? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

fun JsonObject.bool(key: String, default: Boolean = false): Boolean =
    (this[key] as? JsonPrimitive)?.booleanOrNull ?: default

fun JsonObject.obj(key: String): JsonObject = this[key].asObj()

fun JsonObject.arr(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())

// ---------- Protocol (pure, unit-testable) ----------

data class UbusRawResponse(val status: Int, val payload: JsonObject?, val id: Long)

object UbusProtocol {

    private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    fun buildRequest(id: Long, session: String, objectName: String, method: String, args: JsonObject): JsonObject {
        val params = JsonArray(
            listOf(
                JsonPrimitive(session),
                JsonPrimitive(objectName),
                JsonPrimitive(method),
                args,
            )
        )
        return JsonObject(
            linkedMapOf(
                "jsonrpc" to JsonPrimitive("2.0"),
                "id" to JsonPrimitive(id),
                "method" to JsonPrimitive("call"),
                "params" to params,
            )
        )
    }

    fun parseResponse(text: String): UbusRawResponse {
        val root: JsonElement = try {
            lenientJson.parseToJsonElement(text)
        } catch (e: Exception) {
            return UbusRawResponse(UbusCodes.UNKNOWN, null, 0L)
        }
        val obj = root.asObj()
        val id = obj.longOrNull("id") ?: 0L
        val result = obj.arr("result")
        if (result.isEmpty()) return UbusRawResponse(UbusCodes.UNKNOWN, null, id)
        val status = (result[0] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: UbusCodes.UNKNOWN
        val payload = result.getOrNull(1)?.asObj()
        return UbusRawResponse(status, payload, id)
    }

    fun extractSession(payload: JsonObject): String = payload.str("ubus_rpc_session")
}

// ---------- Exec result ----------

data class ExecResult(val code: Int, val stdout: String, val stderr: String)
