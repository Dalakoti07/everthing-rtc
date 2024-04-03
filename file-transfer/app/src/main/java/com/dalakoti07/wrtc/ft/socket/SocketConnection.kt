package com.dalakoti07.wrtc.ft.socket

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

private const val TAG = "SocketConnection"

sealed class SocketEvents{
    data class OnSocketMessageReceived(val message: MessageModel): SocketEvents()
    data class ConnectionChange(val isConnected: Boolean): SocketEvents()
    data class ConnectionError(val error: String): SocketEvents()
}

class SocketConnection {

    private val scope = CoroutineScope(Dispatchers.IO)

    private var webSocket: WebSocketClient? = null
    private val gson = Gson()

    private val _events = MutableSharedFlow<SocketEvents>()
    val event: SharedFlow<SocketEvents>
        get() = _events

    fun initSocket(
        username: String,
    ) {

        webSocket = object : WebSocketClient(URI("ws://192.168.0.108:3000")) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                Log.d(TAG, "onOpen: ${Thread.currentThread()}")
                sendMessageToSocket(
                    MessageModel(
                        "store_user", username, null, null
                    )
                )
            }

            override fun onMessage(message: String?) {
                try {
                    Log.d(TAG, "onMessage: $message")
                    emitEvent(
                        SocketEvents.OnSocketMessageReceived(
                            gson.fromJson(message, MessageModel::class.java)
                        )
                    )
                } catch (e: Exception) {
                    Log.d(TAG, "onMessage: error -> $e")
                    emitEvent(
                        SocketEvents.ConnectionError(
                            e.message ?: "error in receiving messages from socket"
                        )
                    )
                    e.printStackTrace()
                }
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                Log.d(TAG, "onClose: $reason")
                emitEvent(
                    SocketEvents.ConnectionChange(
                        isConnected = false,
                    )
                )
            }

            override fun onError(ex: Exception?) {
                Log.d(TAG, "onError: $ex")
                emitEvent(
                    SocketEvents.ConnectionError(
                        ex?.message ?: "Socket exception"
                    )
                )
            }
        }
        webSocket?.connect()
    }

    private fun emitEvent(event: SocketEvents) {
        scope.launch {
            _events.emit(
                event
            )
        }
    }

    fun sendMessageToSocket(message: MessageModel) {
        try {
            Log.d(TAG, "sendMessageToSocket: $message")
            webSocket?.send(Gson().toJson(message))
        } catch (e: Exception) {
            Log.d(TAG, "sendMessageToSocket: $e")
        }
    }

}