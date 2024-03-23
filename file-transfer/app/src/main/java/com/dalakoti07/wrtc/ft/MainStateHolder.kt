package com.dalakoti07.wrtc.ft

data class MainScreenState(
    val isConnectedToServer: Boolean = false,
    val isConnectToPeer: String? = null,
    val connectedAs: String = "",
    val messagesFromServer: List<String> = emptyList(),
)

sealed class MainActions {
    data class ConnectAs(val name: String) : MainActions()
    data object AcceptIncomingConnection: MainActions()
}
