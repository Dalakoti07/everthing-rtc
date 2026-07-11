package com.dalakoti07.wrtc.ft

import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dalakoti07.wrtc.ft.rtc.FileMeta
import com.dalakoti07.wrtc.ft.rtc.IceCandidateModel
import com.dalakoti07.wrtc.ft.rtc.MessageType
import com.dalakoti07.wrtc.ft.rtc.WebRTCManager
import com.dalakoti07.wrtc.ft.socket.MessageModel
import com.dalakoti07.wrtc.ft.socket.SocketConnection
import com.dalakoti07.wrtc.ft.socket.SocketEvents
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.io.ByteArrayOutputStream
import java.io.File

private const val TAG = "MainViewModel"

class MainViewModel : ViewModel() {

    private val _state = MutableStateFlow(MainScreenState())
    val state: StateFlow<MainScreenState>
        get() = _state

    private lateinit var newOfferMessage: MessageModel

    private val _oneTimeEvents = MutableSharedFlow<MainOneTimeEvents>()
    val oneTimeEvents: Flow<MainOneTimeEvents>
        get() = _oneTimeEvents.asSharedFlow()

    private val socketConnection = SocketConnection()
    private val gson = Gson()
    private lateinit var rtcManager: WebRTCManager

    // File reassembly — accumulate binary chunks until FILE_END
    private var receivingMeta: FileMeta? = null
    private val receivedBuffer = ByteArrayOutputStream()
    private var receivedChunkCount = 0

    init {
        listenToSocketEvents()
    }

    private fun listenToSocketEvents() {
        viewModelScope.launch {
            socketConnection.event.collectLatest {
                when (it) {
                    is SocketEvents.ConnectionChange -> {
                        if (!it.isConnected) {
                            _state.update { s ->
                                s.copy(isConnectedToServer = false, connectedAs = "")
                            }
                        }
                    }
                    is SocketEvents.OnSocketMessageReceived -> handleNewMessage(it.message)
                    is SocketEvents.ConnectionError -> Log.d(TAG, "socket error: ${it.error}")
                }
            }
        }
    }

    private fun handleNewMessage(message: MessageModel) {
        Log.d(TAG, "handleNewMessage: ${message.type}")
        when (message.type) {
            "user_already_exists" -> sendMessageToUi(MessageType.Info("User already exists"))
            "user_stored" -> {
                sendMessageToUi(MessageType.Info("User stored in socket"))
                _state.update { s ->
                    s.copy(isConnectedToServer = true, connectedAs = message.data.toString())
                }
            }
            "transfer_response" -> {
                if (message.data == null) {
                    sendMessageToUi(MessageType.Info("User is not available"))
                    return
                }
                rtcManager = WebRTCManager(
                    socketConnection = socketConnection,
                    userName = state.value.connectedAs,
                    target = message.data.toString(),
                )
                consumeEventsFromRTC()
                rtcManager.updateTarget(message.data.toString())
                sendMessageToUi(MessageType.Info("Connected to ${message.data}"))
                _state.update { s -> s.copy(isConnectToPeer = message.data.toString()) }
                rtcManager.createOffer(
                    from = state.value.connectedAs,
                    target = message.data.toString(),
                )
            }
            "offer_received" -> {
                newOfferMessage = message
                _state.update { s -> s.copy(inComingRequestFrom = message.name.orEmpty()) }
                viewModelScope.launch { _oneTimeEvents.emit(MainOneTimeEvents.GotInvite) }
            }
            "answer_received" -> {
                rtcManager.onRemoteSessionReceived(
                    SessionDescription(SessionDescription.Type.ANSWER, message.data.toString())
                )
            }
            "ice_candidate" -> {
                try {
                    val candidate = gson.fromJson(gson.toJson(message.data), IceCandidateModel::class.java)
                    rtcManager.addIceCandidate(
                        IceCandidate(
                            candidate.sdpMid,
                            Math.toIntExact(candidate.sdpMLineIndex.toLong()),
                            candidate.sdpCandidate
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun consumeEventsFromRTC() {
        viewModelScope.launch {
            // Use collect (not collectLatest) so no event is dropped mid-transfer
            rtcManager.messageStream.collect { event ->
                when (event) {
                    is MessageType.ConnectedToPeer -> {
                        _state.update { s ->
                            s.copy(
                                isRtcEstablished = true,
                                peerConnectionString = "Connected to peer ${s.isConnectToPeer}",
                            )
                        }
                    }

                    // ── Sender-side progress ──────────────────────────────────
                    is MessageType.FileSendProgress -> {
                        _state.update { s ->
                            s.copy(
                                sendProgress = event.progress,
                                sendingFileName = event.fileName,
                            )
                        }
                    }

                    // ── Receiver-side: metadata arrives first ─────────────────
                    is MessageType.FileMetaReceived -> {
                        receivingMeta = event.meta
                        receivedBuffer.reset()
                        receivedChunkCount = 0
                        _state.update { s ->
                            s.copy(
                                receivingFileName = event.meta.name,
                                receiveProgress = 0f,
                                receivedFilePath = null,
                            )
                        }
                        sendMessageToUi(
                            MessageType.Info("Incoming file: ${event.meta.name} (${event.meta.size / 1024} KB)")
                        )
                    }

                    // ── Receiver-side: each binary chunk ──────────────────────
                    is MessageType.FileChunk -> {
                        receivedBuffer.write(event.bytes)
                        receivedChunkCount++
                        val meta = receivingMeta
                        if (meta != null && meta.totalChunks > 0) {
                            val progress = receivedChunkCount.toFloat() / meta.totalChunks
                            _state.update { s -> s.copy(receiveProgress = progress.coerceAtMost(1f)) }
                        }
                    }

                    // ── Receiver-side: all chunks arrived, save file ───────────
                    is MessageType.FileTransferDone -> {
                        val meta = receivingMeta ?: return@collect
                        val path = saveFile(meta.name, receivedBuffer.toByteArray())
                        receivedBuffer.reset()
                        receivedChunkCount = 0
                        receivingMeta = null
                        _state.update { s ->
                            s.copy(receiveProgress = 1f, receivedFilePath = path)
                        }
                        sendMessageToUi(MessageType.Info("File saved: ${meta.name}"))
                    }

                    else -> sendMessageToUi(event)
                }
            }
        }
    }

    private fun saveFile(name: String, bytes: ByteArray): String {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(dir, name)
        file.writeBytes(bytes)
        Log.d(TAG, "File saved: ${file.absolutePath}")
        return file.absolutePath
    }

    private fun sendMessageToUi(msg: MessageType) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { s ->
                s.copy(messagesFromServer = s.messagesFromServer + msg)
            }
        }
    }

    fun dispatchAction(actions: MainActions) {
        when (actions) {
            is MainActions.ConnectAs -> socketConnection.initSocket(actions.name)

            is MainActions.AcceptIncomingConnection -> {
                val session = SessionDescription(
                    SessionDescription.Type.OFFER,
                    newOfferMessage.data.toString()
                )
                if (!::rtcManager.isInitialized) {
                    rtcManager = WebRTCManager(
                        socketConnection = socketConnection,
                        userName = state.value.connectedAs,
                        target = newOfferMessage.name.toString(),
                    )
                    consumeEventsFromRTC()
                }
                rtcManager.onRemoteSessionReceived(session)
                rtcManager.answerToOffer(newOfferMessage.name)
            }

            is MainActions.ConnectToUser -> {
                socketConnection.sendMessageToSocket(
                    MessageModel(
                        type = "start_transfer",
                        name = state.value.connectedAs,
                        target = actions.name,
                        data = null,
                    )
                )
            }

            is MainActions.SendChatMessage -> rtcManager.sendMessage(actions.msg)

            is MainActions.SendFile -> rtcManager.sendFile(actions.uri)
        }
    }
}
