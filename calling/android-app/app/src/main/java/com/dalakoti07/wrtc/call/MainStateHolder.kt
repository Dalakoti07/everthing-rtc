package com.dalakoti07.wrtc.call

import com.dalakoti07.wrtc.call.rtc.MessageType

data class MainScreenState(
    val isConnectedToServer: Boolean = false,
    val isConnectToPeer: String? = null,
    val connectedAs: String = "",
    val messagesFromServer: List<MessageType> = emptyList(),
    val inComingRequestFrom: String = "",
    val isRtcEstablished: Boolean = false,
    val peerConnectionString: String = "",
) {
    companion object {
        fun forPreview(): MainScreenState {
            return MainScreenState(
                isConnectedToServer = true,
                isConnectToPeer = "Moto",
                connectedAs = "P6",
                messagesFromServer = listOf(
                    MessageType.Info("Connected to Server as P6"),
                    MessageType.Info("Connected to Peer Moto"),
                ),
            )
        }
    }
}

sealed class MainActions {
    data class ConnectAs(val name: String) : MainActions()
    data object AcceptIncomingConnection: MainActions()
    data class ConnectToUser(val name: String): MainActions()

    data class EndCall(val msg: String): MainActions()
}

sealed class MainOneTimeEvents {
    object GotInvite: MainOneTimeEvents()
}
