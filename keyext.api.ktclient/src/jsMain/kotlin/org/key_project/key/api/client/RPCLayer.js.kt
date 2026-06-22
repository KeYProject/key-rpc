package org.key_project.key.api.client

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.w3c.dom.WebSocket
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.Json
import kotlin.js.json

/**
 * JSON-RPC 2.0 Layer implementation for JavaScript using WebSockets.
 * 
 * @author Alexander Weigl 
 * @version 1 (22.06.26)
 */
actual class RPCLayer {
    var websocket: WebSocket? = null
    val pendingRequests = mutableMapOf<Int, RequestHandler>()
    private val notificationChannel = Channel<JsonRpcNotification>(Channel.UNLIMITED)
    var requestIdCounter = 0

    /**
     * Represents a pending request with its continuation.
     */
    data class RequestHandler(
        val continuation: Continuation<Result<Any>>,
        val methodName: String
    )

    /**
     * Connects to the JSON-RPC server via WebSocket.
     * @param url The WebSocket URL of the RPC server
     */
    fun connect(url: String): Job = CoroutineScope(Dispatchers.Main).launch {
        websocket = WebSocket(url).also { ws ->
            ws.onopen = {
                console.log("WebSocket connected to $url")
            }
            ws.onmessage = { event ->
                handleIncomingMessage(event.data.toString())
            }
            ws.onerror = { error ->
                console.error("WebSocket error: $error")
            }
            ws.onclose = { event ->
                console.log("WebSocket closed: ${event.code} - ${event.reason}")
                // Fail all pending requests
                pendingRequests.values.forEach { handler ->
                    handler.continuation.resumeWithException(
                        RpcException("Connection closed", code = -1)
                    )
                }
                pendingRequests.clear()
            }
        }
    }

    /**
     * Disconnects from the WebSocket server.
     */
    fun disconnect() {
        websocket?.close()
        websocket = null
    }

    private fun handleIncomingMessage(data: String) {
        try {
            val parsed = JSON.parse<dynamic>(data)

            // Check if this is a response (has "id" field)
            if (parsed.id != null && parsed.id != undefined) {
                val id = parsed.id as Int
                val handler = pendingRequests.remove(id)

                if (handler != null) {
                    when {
                        parsed.error != null && parsed.error != undefined -> {
                            val error = parsed.error
                            val errorCode = error.code as? Int ?: -1
                            val errorMessage = error.message as? String ?: "Unknown error"
                            handler.continuation.resumeWithException(
                                RpcException(errorMessage, code = errorCode)
                            )
                        }

                        parsed.result != null && parsed.result != undefined -> {
                            handler.continuation.resume(Result.success(parsed.result))
                        }

                        else -> {
                            handler.continuation.resumeWithException(
                                RpcException("Invalid response format", code = -32600)
                            )
                        }
                    }
                } else {
                    console.warn("Received response for unknown request id: $id")
                }
            } else {
                // This is a notification
                val method = parsed.method as? String ?: ""
                val params = parsed.params
                notificationChannel.trySend(JsonRpcNotification(method, params))
            }
        } catch (e: Exception) {
            console.error("Failed to parse incoming message: $e")
        }
    }

    actual suspend inline fun <reified T> callSync(methodName: String, vararg params: Any): T {
        val id = ++requestIdCounter

        suspendCoroutine { continuation ->
            // Build JSON-RPC request using js dynamic object
            val request = buildJsonRpcRequest(methodName, params.toList(), id)
            val jsonString = JSON.stringify(request)

            pendingRequests[id] = RequestHandler(
                continuation = continuation as Continuation<Result<Any>>,
                methodName = methodName
            )

            websocket?.send(jsonString) ?: continuation.resumeWithException(RpcException("Not connected to server"))
        }
        return waitFor(id)?.convertToType()
    }

    fun waitFor(id: Any): Json {
        TODO("Not yet implemented")
    }

    actual inline fun callAsync(methodName: String, vararg params: Any) {
        // No ID means notification (no response expected)
        val request = buildJsonRpcRequest(methodName, params.toList(), null)
        val jsonString = JSON.stringify(request)

        websocket?.send(jsonString)
            ?: throw RpcException("Not connected to server")
    }

    /**
     * Builds a JSON-RPC 2.0 request object.
     */
    fun buildJsonRpcRequest(method: String, params: List<Any>, id: Int?): dynamic {
        return json(
            "jsonrpc" to "2.0",
            "method" to method,
            "params" to params.map { convertToJsonValue(it) }.toTypedArray(),
            "id" to (id ?: undefined)
        )
    }

    /**
     * Converts a Kotlin/JS value to a JSON-compatible dynamic value.
     */
    private fun convertToJsonValue(value: Any?): dynamic {
        return when (value) {
            null -> null
            is String -> value
            is Number -> value
            is Boolean -> value
            is Enum<*> -> value.name
            is Map<*, *> -> jsObjectFromMap(value)
            is List<*> -> value.map { convertToJsonValue(it) }.toTypedArray()
            is Array<*> -> value.map { convertToJsonValue(it) }.toTypedArray()
            else -> value.toString()
        }
    }

    /**
     * Creates a JS object from a Kotlin map.
     */
    private fun jsObjectFromMap(map: Map<*, *>): dynamic {
        val obj = js("{}")
        map.forEach { (key, value) ->
            if (key != null) {
                obj[key.toString()] = convertToJsonValue(value)
            }
        }
        return obj
    }

    /**
     * Converts a dynamic JS result to the expected Kotlin type.
     */
    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> convertToType(result: dynamic): T {
        return when {
            result === null || result == undefined -> null as T
            result is Boolean -> result as T
            result is Number -> {
                when (T::class) {
                    Int::class -> result.toInt() as T
                    Long::class -> result.toLong() as T
                    Double::class -> result.toDouble() as T
                    Float::class -> result.toFloat() as T
                    Short::class -> result.toShort() as T
                    Byte::class -> result.toByte() as T
                    else -> result as T
                }
            }

            result is String -> {
                when (T::class) {
                    String::class -> result as T
                    Int::class -> result.toInt() as T
                    Long::class -> result.toLong() as T
                    Double::class -> result.toDouble() as T
                    else -> result as T
                }
            }

            else -> result as T
        }
    }

    /**
     * Checks if the WebSocket connection is open.
     */
    fun isConnected(): Boolean = websocket?.readyState == WebSocket.OPEN

    /**
     * Returns a flow of incoming notifications.
     */
    fun notifications() = notificationChannel.receiveAsFlow()
}

/**
 * Represents an incoming JSON-RPC notification.
 */
private data class JsonRpcNotification(
    val method: String,
    val params: dynamic
)

/**
 * RPC Exception carrying error information from the server.
 */
class RpcException(message: String, val code: Int = -1) : Exception(message)