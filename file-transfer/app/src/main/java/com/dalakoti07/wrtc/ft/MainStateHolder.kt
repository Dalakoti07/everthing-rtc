package com.dalakoti07.wrtc.ft

import android.net.Uri
import com.dalakoti07.wrtc.ft.rtc.MessageType

data class MainScreenState(
    val isConnectedToServer: Boolean = false,
    val isConnectToPeer: String? = null,
    val connectedAs: String = "",
    val messagesFromServer: List<MessageType> = emptyList(),
    val inComingRequestFrom: String = "",
    val isRtcEstablished: Boolean = false,
    val peerConnectionString: String = "",
    // file send progress (0f = idle, 1f = done)
    val sendProgress: Float = 0f,
    val sendingFileName: String = "",
    // file receive progress (0f = idle, 1f = done)
    val receiveProgress: Float = 0f,
    val receivingFileName: String = "",
    val receivedFilePath: String? = null,
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
                    MessageType.MessageByMe("Hello Moto Edge"),
                    MessageType.MessageByPeer("Hi P6, we have"),
                    MessageType.MessageByMe("Which Android OS version are you running???"),
                ),
                sendProgress = 0.6f,
                sendingFileName = "photo.jpg",
                receiveProgress = 0.3f,
                receivingFileName = "video.mp4",
            )
        }
    }
}

sealed class MainActions {
    data class ConnectAs(val name: String) : MainActions()
    data object AcceptIncomingConnection : MainActions()
    data class ConnectToUser(val name: String) : MainActions()
    data class SendChatMessage(val msg: String) : MainActions()
    data class SendFile(val uri: Uri) : MainActions()
}

sealed class MainOneTimeEvents {
    object GotInvite : MainOneTimeEvents()
}
