package com.dalakoti07.wrtc.ft.rtc

import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.dalakoti07.wrtc.ft.TransferApp
import com.dalakoti07.wrtc.ft.socket.MessageModel
import com.dalakoti07.wrtc.ft.socket.SocketConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.nio.ByteBuffer

private const val TAG = "WebRtcManager"
private const val CHUNK_SIZE = 16 * 1024 // 16KB

data class FileMeta(val name: String, val size: Long, val totalChunks: Int)

sealed class MessageType {
    data class Info(val msg: String) : MessageType()
    data class MessageByMe(val msg: String) : MessageType()
    data class MessageByPeer(val msg: String) : MessageType()
    data object ConnectedToPeer : MessageType()
    data class FileMetaReceived(val meta: FileMeta) : MessageType()
    data class FileChunk(val bytes: ByteArray) : MessageType()
    data object FileTransferDone : MessageType()
    data class FileSendProgress(val progress: Float, val fileName: String) : MessageType()
}

class WebRTCManager(
    private var target: String,
    private val socketConnection: SocketConnection,
    private val userName: String,
) : PeerConnection.Observer {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val _messageStream = MutableSharedFlow<MessageType>()
    val messageStream: SharedFlow<MessageType>
        get() = _messageStream

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:iphone-stun.strato-iphone.de:3478")
            .createIceServer(),
        PeerConnection.IceServer("stun:openrelay.metered.ca:80"),
        PeerConnection.IceServer(
            "turn:openrelay.metered.ca:80",
            "openrelayproject",
            "openrelayproject"
        ),
        PeerConnection.IceServer(
            "turn:openrelay.metered.ca:443",
            "openrelayproject",
            "openrelayproject"
        ),
        PeerConnection.IceServer(
            "turn:openrelay.metered.ca:443?transport=tcp",
            "openrelayproject",
            "openrelayproject"
        ),
    )
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private lateinit var peerConnection: PeerConnection
    private lateinit var dataChannel: DataChannel

    init {
        initializePeerConnectionFactory()
        createPeerConnection()
        createDataChannel("localDataChannel")
    }

    fun updateTarget(name: String) {
        target = name
    }

    private fun initializePeerConnectionFactory() {
        val options = PeerConnectionFactory
            .InitializationOptions
            .builder(TransferApp.getContext())
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

    private fun createPeerConnection() {
        peerConnection = peerConnectionFactory.createPeerConnection(iceServers, this)!!
    }

    private fun createDataChannel(label: String) {
        dataChannel = peerConnection.createDataChannel(label, DataChannel.Init())
        dataChannel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(amount: Long) {
                Log.d(TAG, "onBufferedAmountChange: $amount")
            }
            override fun onStateChange() {
                Log.d(TAG, "data channel onStateChange")
            }
            override fun onMessage(buffer: DataChannel.Buffer?) {
                consumeDataChannelData(buffer)
            }
        })
    }

    private fun consumeDataChannelData(buffer: DataChannel.Buffer?) {
        buffer ?: return
        val data = buffer.data
        val bytes = ByteArray(data.remaining())
        data.get(bytes)

        if (buffer.binary) {
            scope.launch {
                _messageStream.emit(MessageType.FileChunk(bytes))
            }
        } else {
            val message = String(bytes, Charsets.UTF_8)
            Log.d(TAG, "Received text message: ${message.take(120)}")
            scope.launch {
                when {
                    message.startsWith("FILE_META:") -> {
                        try {
                            val meta = parseFileMeta(message.removePrefix("FILE_META:"))
                            _messageStream.emit(MessageType.FileMetaReceived(meta))
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing FILE_META: $e")
                        }
                    }
                    message == "FILE_END" -> {
                        _messageStream.emit(MessageType.FileTransferDone)
                    }
                    message.isNotEmpty() -> {
                        _messageStream.emit(MessageType.MessageByPeer(message))
                    }
                }
            }
        }
    }

    // Manual parse — avoids Gson dependency on a hot path and keeps it simple
    private fun parseFileMeta(json: String): FileMeta {
        val name = json.substringAfter("\"name\":\"").substringBefore("\"")
        val size = json.substringAfter("\"size\":").substringBefore(",").substringBefore("}").trim().toLong()
        val totalChunks = json.substringAfter("\"totalChunks\":").substringBefore("}").trim().toInt()
        return FileMeta(name, size, totalChunks)
    }

    fun sendFile(uri: Uri) {
        scope.launch {
            val cr = TransferApp.getContext().contentResolver

            val name = cr.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst())
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                else null
            } ?: "file"

            val size = cr.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
            val totalChunks = ((size + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()
            Log.d(TAG, "sendFile: name=$name size=$size chunks=$totalChunks")

            val metaMsg = """FILE_META:{"name":"$name","size":$size,"totalChunks":$totalChunks}"""
            dataChannel.send(DataChannel.Buffer(ByteBuffer.wrap(metaMsg.toByteArray(Charsets.UTF_8)), false))
            _messageStream.emit(MessageType.FileSendProgress(0f, name))

            var chunksSent = 0
            cr.openInputStream(uri)?.use { stream ->
                val buf = ByteArray(CHUNK_SIZE)
                var read: Int
                while (stream.read(buf).also { read = it } != -1) {
                    // Flow control: pause if the send buffer is backed up
                    while (dataChannel.bufferedAmount() > 1_000_000L) {
                        delay(10)
                    }
                    dataChannel.send(DataChannel.Buffer(ByteBuffer.wrap(buf.copyOf(read)), true))
                    chunksSent++
                    val progress = chunksSent.toFloat() / totalChunks
                    _messageStream.emit(MessageType.FileSendProgress(progress.coerceAtMost(1f), name))
                }
            }

            dataChannel.send(DataChannel.Buffer(ByteBuffer.wrap("FILE_END".toByteArray(Charsets.UTF_8)), false))
            _messageStream.emit(MessageType.FileSendProgress(1f, name))
            Log.d(TAG, "sendFile complete: $name")
        }
    }

    fun createOffer(from: String, target: String) {
        Log.d(TAG, "user is available creating offer")
        val sdpObserver = object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(desc: SessionDescription?) {}
                    override fun onSetSuccess() {
                        Log.d(TAG, "onSetSuccess offer")
                        socketConnection.sendMessageToSocket(
                            MessageModel(
                                "create_offer", from, target,
                                hashMapOf("sdp" to desc?.description, "type" to desc?.type)
                            )
                        )
                    }
                    override fun onCreateFailure(error: String?) { Log.e(TAG, "offer create fail $error") }
                    override fun onSetFailure(error: String?) { Log.e(TAG, "offer set fail $error") }
                }, desc)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {}
        }
        peerConnection.createOffer(sdpObserver, MediaConstraints())
    }

    fun addIceCandidate(p0: IceCandidate?) {
        peerConnection.addIceCandidate(p0)
    }

    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}

    override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
        when (newState) {
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED -> {
                Log.d(TAG, "ICE Connected")
                scope.launch { _messageStream.emit(MessageType.ConnectedToPeer) }
            }
            else -> Log.d(TAG, "ICE state: $newState")
        }
    }

    override fun onIceConnectionReceivingChange(p0: Boolean) {}
    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}

    override fun onIceCandidate(p0: IceCandidate?) {
        Log.d(TAG, "onIceCandidate called")
        addIceCandidate(p0)
        socketConnection.sendMessageToSocket(
            MessageModel(
                "ice_candidate", userName, target,
                hashMapOf(
                    "sdpMid" to p0?.sdpMid,
                    "sdpMLineIndex" to p0?.sdpMLineIndex,
                    "sdpCandidate" to p0?.sdp
                )
            )
        )
    }

    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
    override fun onAddStream(p0: MediaStream?) {}
    override fun onRemoveStream(p0: MediaStream?) {}

    override fun onDataChannel(p0: DataChannel?) {
        Log.d(TAG, "onDataChannel: called for peers")
        p0!!.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(p0: Long) {}
            override fun onStateChange() {}
            override fun onMessage(p0: DataChannel.Buffer?) {
                consumeDataChannelData(p0)
            }
        })
    }

    override fun onRenegotiationNeeded() {}

    fun onRemoteSessionReceived(session: SessionDescription) {
        peerConnection.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, session)
    }

    fun answerToOffer(lTarget: String?) {
        peerConnection.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        socketConnection.sendMessageToSocket(
                            MessageModel(
                                "create_answer", userName, lTarget,
                                hashMapOf("sdp" to desc?.description, "type" to desc?.type)
                            )
                        )
                    }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {}
                }, desc)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, MediaConstraints())
    }

    fun sendMessage(msg: String) {
        if (msg.isEmpty()) return
        scope.launch { _messageStream.emit(MessageType.MessageByMe(msg)) }
        dataChannel.send(DataChannel.Buffer(ByteBuffer.wrap(msg.toByteArray(Charsets.UTF_8)), false))
    }
}
