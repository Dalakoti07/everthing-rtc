package com.dalakoti07.wrtc.ft

import com.dalakoti07.wrtc.ft.rtc.MessageType

data class MainScreenState(
    val isConnectedToServer: Boolean = false,
    val isConnectToPeer: String? = null,
    val connectedAs: String = "",
    val messagesFromServer: List<MessageType> = emptyList(),
    val inComingRequestFrom: String = "",
    val isRtcEstablished: Boolean = false,
    val peerConnectionString: String = "",
)

sealed class MainActions {
    data class ConnectAs(val name: String) : MainActions()
    data object AcceptIncomingConnection: MainActions()
    data class ConnectToUser(val name: String): MainActions()

    data class SendChatMessage(val msg: String): MainActions()
}

sealed class MainOneTimeEvents {
    object GotInvite: MainOneTimeEvents()
}
