package com.dalakoti07.wrtc.call

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dalakoti07.wrtc.call.audio.AudioRecorder
import com.dalakoti07.wrtc.call.rtc.IceCandidateModel
import com.dalakoti07.wrtc.call.rtc.MessageType
import com.dalakoti07.wrtc.call.rtc.WebRTCManager
import com.dalakoti07.wrtc.call.socket.MessageModel
import com.dalakoti07.wrtc.call.socket.SocketConnection
import com.dalakoti07.wrtc.call.socket.SocketEvents
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

private const val TAG = "MainViewModel"

class MainViewModel : ViewModel() {

    private val _state = MutableStateFlow(
        MainScreenState()
    )
    val state: StateFlow<MainScreenState>
        get() = _state

    private lateinit var newOfferMessage: MessageModel

    private val _oneTimeEvents = MutableSharedFlow<MainOneTimeEvents>()
    val oneTimeEvents: Flow<MainOneTimeEvents>
        get() = _oneTimeEvents.asSharedFlow()

    private val socketConnection = SocketConnection()
    private val gson = Gson()
    private lateinit var rtcManager: WebRTCManager
    private lateinit var audioRecorder: AudioRecorder

    init {
        listenToSocketEvents()
    }

    private fun listenToSocketEvents() {
        viewModelScope.launch {
            socketConnection.event.collectLatest {
                when (it) {
                    is SocketEvents.ConnectionChange -> {
                        if (!it.isConnected) {
                            _state.update {
                                state.value.copy(
                                    isConnectedToServer = false,
                                    connectedAs = "",
                                )
                            }
                        }
                    }

                    is SocketEvents.OnSocketMessageReceived -> {
                        handleNewMessage(it.message)
                    }

                    is SocketEvents.ConnectionError -> {
                        Log.d(TAG, "socket ConnectionError ${it.error}")
                    }
                }
            }
        }
    }

    private fun handleNewMessage(message: MessageModel) {
        Log.d(TAG, "handleNewMessage in VM")
        when (message.type) {
            "user_already_exists" -> {
                sendMessageToUi(MessageType.Info("User already exists"))
            }
            "user_stored" -> {
                Log.d(TAG, "User stored in socket")
                sendMessageToUi(MessageType.Info("User stored in socket"))
                _state.update {
                    state.value.copy(
                        isConnectedToServer = true,
                        connectedAs = message.data.toString(),
                    )
                }
            }
            "call_response" -> {
                Log.d(TAG, "transfer_response: ")
                // user is online / offline
                if (message.data == null) {
                    sendMessageToUi(MessageType.Info("User is not available"))
                    return
                }
                // important to update target
                rtcManager = WebRTCManager(
                    socketConnection = socketConnection,
                    userName = state.value.connectedAs,
                    target = message.data.toString(),
                )
                consumeEventsFromRTC()
                rtcManager.updateTarget(message.data.toString())
                sendMessageToUi(MessageType.Info("User is Connected to ${message.data}"))
                _state.update {
                    state.value.copy(
                        isConnectToPeer = message.data.toString(),
                    )
                }
                rtcManager.createOffer(
                    from = state.value.connectedAs,
                    target = message.data.toString(),
                )
            }
            "offer_received" -> {
                newOfferMessage = message
                Log.d(TAG, "offer_received ")
                _state.update {
                    state.value.copy(
                        inComingRequestFrom = message.name.orEmpty(),
                    )
                }
                viewModelScope.launch {
                    _oneTimeEvents.emit(
                        MainOneTimeEvents.GotInvite
                    )
                }
            }
            "answer_received" -> {
                val session = SessionDescription(
                    SessionDescription.Type.ANSWER,
                    message.data.toString()
                )
                Log.d(TAG, "onNewMessage: answer received $session")
                rtcManager.onRemoteSessionReceived(session)
            }
            "ice_candidate" -> {
                try {
                    val receivingCandidate = gson.fromJson(
                        gson.toJson(message.data),
                        IceCandidateModel::class.java
                    )
                    Log.d(TAG, "onNewMessage: ice candidate $receivingCandidate")
                    rtcManager.addIceCandidate(
                        IceCandidate(
                            receivingCandidate.sdpMid,
                            Math.toIntExact(receivingCandidate.sdpMLineIndex.toLong()),
                            receivingCandidate.sdpCandidate
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
            rtcManager.messageStream.collectLatest {
                if(it is MessageType.ConnectedToPeer){
                    _state.update {
                        state.value.copy(
                            isRtcEstablished = true,
                            peerConnectionString = "Is connected to peer ${state.value.isConnectToPeer}",
                        )
                    }
                }
                sendMessageToUi(msg = it)
            }
        }
    }

    private fun sendMessageToUi(msg: MessageType) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                state.value.copy(
                    messagesFromServer = state.value.messagesFromServer + msg,
                )
            }
        }
    }

    fun dispatchAction(actions: MainActions) {
        when (actions) {
            is MainActions.ConnectAs -> {
                socketConnection.initSocket(actions.name)
            }
            is MainActions.AcceptIncomingConnection -> {
                // todo do add view for confirmation, and then take further actions
                val session = SessionDescription(
                    SessionDescription.Type.OFFER,
                    newOfferMessage.data.toString()
                )
                // move to new place
                if(!::rtcManager.isInitialized){
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
                        type = "start_call",
                        name = state.value.connectedAs,
                        target = actions.name,
                        data = null,
                    )
                )
            }
            is MainActions.EndCall->{
                rtcManager.peerConnection.close()
                //rtcManager.setLocalAudioStream()
            }
        }
    }

}