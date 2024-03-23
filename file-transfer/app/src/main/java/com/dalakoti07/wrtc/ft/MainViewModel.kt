package com.dalakoti07.wrtc.ft

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "MainViewModel"

class MainViewModel: ViewModel() {

    private val _state = MutableStateFlow(
        MainScreenState()
    )
    val state: StateFlow<MainScreenState>
        get() = _state

    private val socketConnection = SocketConnection()

    init {
        listenToSocketEvents()
    }

    private fun listenToSocketEvents() {
        viewModelScope.launch {
            socketConnection.event.collectLatest {
                when(it){
                    is SocketEvents.ConnectionChange->{
                        if(!it.isConnected){
                            _state.update {
                                state.value.copy(
                                    isConnectedToServer = false,
                                    connectedAs = "",
                                )
                            }
                        }
                    }
                    is SocketEvents.OnSocketMessageReceived->{
                        handleNewMessage(it.message)
                    }
                    is SocketEvents.ConnectionError->{
                        Log.d(TAG, "socket ConnectionError ${it.error}")
                    }
                }
            }
        }
    }

    private fun handleNewMessage(message: MessageModel) {
        Log.d(TAG, "handleNewMessage in VM")
        when(message.type){
            "user_already_exists"->{

            }
            "user_stored"->{
                Log.d(TAG, "user stored in socket")
                _state.update {
                    state.value.copy(
                        isConnectedToServer = true,
                        connectedAs = message.data.toString(),
                    )
                }
            }
            "transfer_response"->{
                // user is online / offline
            }
            "offer_received"->{}
            "answer_received"->{

            }
            "ice_candidate"->{}
        }
    }

    fun dispatchAction(actions: MainActions){
        when(actions){
            is MainActions.ConnectAs->{
                socketConnection.initSocket(actions.name)
            }
            is MainActions.AcceptIncomingConnection->{

            }
        }
    }

}