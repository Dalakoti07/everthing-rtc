package com.dalakoti07.wrtc.ft

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dalakoti07.wrtc.ft.rtc.IceCandidateModel
import com.dalakoti07.wrtc.ft.rtc.MessageType
import com.dalakoti07.wrtc.ft.rtc.WebRTCManager
import com.dalakoti07.wrtc.ft.socket.MessageModel
import com.dalakoti07.wrtc.ft.socket.SocketConnection
import com.dalakoti07.wrtc.ft.socket.SocketEvents
import com.google.gson.Gson
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

private const val TAG = "MainViewModel"

class MainViewModel : ViewModel() {

    private val _state = MutableStateFlow(MainScreenState())
    val state: StateFlow<MainScreenState> get() = _state

    private lateinit var newOfferMessage: MessageModel

    private val _oneTimeEvents = MutableSharedFlow<MainOneTimeEvents>()
    val oneTimeEvents: Flow<MainOneTimeEvents> get() = _oneTimeEvents.asSharedFlow()

    private val socketConnection = SocketConnection()
    private val gson = Gson()
    private lateinit var rtcManager: WebRTCManager

    init {
        listenToSocketEvents()
    }

    private fun listenToSocketEvents() {
        viewModelScope.launch {
            socketConnection.event.collectLatest {
                when (it) {
                    is SocketEvents.ConnectionChange -> {
                        if (!it.isConnected) {
                            _state.update { s -> s.copy(isConnectedToServer = false, connectedAs = "") }
                        }
                    }
                    is SocketEvents.OnSocketMessageReceived -> handleNewMessage(it.message)
                    is SocketEvents.ConnectionError -> Log.d(TAG, "socket error: ${it.error}")
                }
            }
        }
    }

    private fun handleNewMessage(message: MessageModel) {
        when (message.type) {
            "user_already_exists" -> Log.d(TAG, "user already exists")
            "user_stored" -> {
                _state.update { s -> s.copy(isConnectedToServer = true, connectedAs = message.data.toString()) }
            }
            "transfer_response" -> {
                if (message.data == null) return
                rtcManager = WebRTCManager(
                    socketConnection = socketConnection,
                    userName = state.value.connectedAs,
                    target = message.data.toString(),
                )
                consumeEventsFromRTC()
                rtcManager.updateTarget(message.data.toString())
                rtcManager.createOffer(from = state.value.connectedAs, target = message.data.toString())
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
            rtcManager.messageStream.collect { event ->
                when (event) {
                    is MessageType.ConnectedToPeer -> {
                        _state.update { s ->
                            s.copy(
                                isRtcEstablished = true,
                                connectedToPeer = s.connectedAs,
                            )
                        }
                    }
                    is MessageType.Info -> Log.d(TAG, "RTC info: ${event.msg}")
                }
            }
        }
    }

    fun dispatchAction(actions: MainActions) {
        when (actions) {
            is MainActions.ConnectAs -> socketConnection.initSocket(actions.name)

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

            is MainActions.MuteAudio -> {
                rtcManager.muteAudio(actions.mute)
                _state.update { s -> s.copy(isMuted = actions.mute) }
            }

            is MainActions.ToggleSpeaker -> {
                rtcManager.toggleSpeaker(actions.on)
                _state.update { s -> s.copy(isSpeakerOn = actions.on) }
            }

            is MainActions.EndCall -> {
                rtcManager.endCall()
                _state.update { s -> s.copy(isRtcEstablished = false, connectedToPeer = "") }
            }
        }
    }
}
