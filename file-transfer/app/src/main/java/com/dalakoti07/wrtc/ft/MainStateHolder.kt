package com.dalakoti07.wrtc.ft

import android.net.Uri

data class MainScreenState(
    val isConnectedToServer: Boolean = false,
    val connectedAs: String = "",
    val inComingRequestFrom: String = "",
    val isRtcEstablished: Boolean = false,
    val connectedToPeer: String = "",
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = true,
)

sealed class MainActions {
    data class ConnectAs(val name: String) : MainActions()
    data class ConnectToUser(val name: String) : MainActions()
    data object AcceptIncomingConnection : MainActions()
    data class MuteAudio(val mute: Boolean) : MainActions()
    data class ToggleSpeaker(val on: Boolean) : MainActions()
    data object EndCall : MainActions()
}

sealed class MainOneTimeEvents {
    object GotInvite : MainOneTimeEvents()
}
