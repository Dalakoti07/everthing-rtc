package com.dalakoti07.wrtc.call.rtc

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.dalakoti07.wrtc.call.CallApp
import com.dalakoti07.wrtc.call.socket.MessageModel
import com.dalakoti07.wrtc.call.socket.SocketConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import java.nio.ByteBuffer

private const val TAG = "WebRtcManager"

sealed class MessageType{
    data class Info(val msg: String): MessageType()
    data object ConnectedToPeer : MessageType()
}

class WebRTCManager(
    private var target: String,
    private val socketConnection: SocketConnection,
    private val userName: String,
): PeerConnection.Observer {

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

    fun updateTarget(name: String){
        target = name
    }

    private fun initializePeerConnectionFactory() {
        val options = PeerConnectionFactory
            .InitializationOptions
            .builder(CallApp.getContext())
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val peerConnectionFactoryOptions = PeerConnectionFactory.Options()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(peerConnectionFactoryOptions)
            .createPeerConnectionFactory()
    }

    private fun createPeerConnection() {
        peerConnection =
            peerConnectionFactory.createPeerConnection(iceServers, this)!!
    }

    private fun createDataChannel(label: String) {
        val init = DataChannel.Init()
        dataChannel = peerConnection.createDataChannel(label, init)
        dataChannel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(amount: Long) {
                Log.d(TAG, "data channel onBufferedAmountChange: ")
            }
            override fun onStateChange() {
                Log.d(TAG, "data channel onStateChange ")
            }
            override fun onMessage(buffer: DataChannel.Buffer?) {
                Log.d(TAG, "onMessage: at line 86")
            }
        })
    }

    fun createOffer(from: String, target: String){
        Log.d(TAG, "user is available creating offer")
        val sdpObserver = object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(desc: SessionDescription?) {
                        Log.d(TAG, "onCreateSuccess: .... using socket to notify peer")
                    }
                    override fun onSetSuccess() {
                        Log.d(TAG, "onSetSuccess: ")
                        val offer = hashMapOf(
                            "sdp" to desc?.description,
                            "type" to desc?.type
                        )

                        socketConnection.sendMessageToSocket(
                            MessageModel(
                                "create_offer", from, target, offer
                            )
                        )
                    }
                    override fun onCreateFailure(error: String?) {
                        Log.d(TAG, "error in creating offer $error")
                    }
                    override fun onSetFailure(error: String?) {
                        Log.d(TAG, "onSetFailure: err-> $error")
                    }
                }, desc)
                // Send offer to signaling server
                // Signaling server will forward it to the other peer
                // Upon receiving answer, set it as remote description
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {}
        }

        val mediaConstraints = MediaConstraints()
        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        peerConnection.createOffer(sdpObserver, mediaConstraints)
    }

    fun addIceCandidate(p0: IceCandidate?) {
        peerConnection.addIceCandidate(p0)
    }

    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
    }

    override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
        when (newState) {
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED -> {
                // Peers are connected
                Log.d(TAG, "ICE Connection State: Connected ")
                scope.launch {
                    _messageStream.emit(
                        MessageType.ConnectedToPeer
                    )
                }
            }
            else -> {
                // Peers are not connected
                Log.d(TAG, "ICE Connection State: not Connected")
            }
        }
    }

    override fun onIceConnectionReceivingChange(p0: Boolean) {
    }

    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
    }

    override fun onIceCandidate(p0: IceCandidate?) {
        Log.d(TAG, "onIceCandidate called ....")
        addIceCandidate(p0)
        val candidate = hashMapOf(
            "sdpMid" to p0?.sdpMid,
            "sdpMLineIndex" to p0?.sdpMLineIndex,
            "sdpCandidate" to p0?.sdp
        )
        socketConnection.sendMessageToSocket(
            MessageModel("ice_candidate",userName,target,candidate)
        )
    }

    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
    }

    override fun onAddStream(mediaStream: MediaStream?) {
        /*mediaStream?.audioTracks?.forEach { _audioTrack ->
            _audioTrack.setEnabled(true)
            // Create an AudioTrack to play the audio
            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(44100) // Adjust based on the audio data received
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val bufferSize = AudioTrack.getMinBufferSize(audioFormat.sampleRate, audioFormat.channelMask, audioFormat.encoding)
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            // Start playback
            audioTrack.play()

            // Attach the audio track to the media stream track
            audioTrack.attachToTrack(audioTrack)
        }*/
    }

    override fun onRemoveStream(p0: MediaStream?) {
    }

    override fun onDataChannel(p0: DataChannel?) {
        Log.d(TAG, "onDataChannel: called for peers")
        p0!!.registerObserver(object: DataChannel.Observer{
            override fun onBufferedAmountChange(p0: Long) {
            }

            override fun onStateChange() {
            }

            override fun onMessage(p0: DataChannel.Buffer?) {
                Log.d(TAG, "onMessage: at line 196")
            }
        })
    }

    override fun onRenegotiationNeeded() {
    }

    fun onRemoteSessionReceived(session: SessionDescription) {
        peerConnection.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {

            }

            override fun onSetSuccess() {
            }

            override fun onCreateFailure(p0: String?) {
            }

            override fun onSetFailure(p0: String?) {
            }

        }, session)
    }

    fun answerToOffer(lTarget: String?) {
        val constraints = MediaConstraints()
        peerConnection.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {
                    }

                    override fun onSetSuccess() {
                        val answer = hashMapOf(
                            "sdp" to desc?.description,
                            "type" to desc?.type
                        )
                        socketConnection.sendMessageToSocket(
                            MessageModel(
                                "create_answer", userName, lTarget, answer
                            )
                        )
                    }

                    override fun onCreateFailure(p0: String?) {
                    }

                    override fun onSetFailure(p0: String?) {
                    }

                }, desc)
            }

            override fun onSetSuccess() {
            }

            override fun onCreateFailure(p0: String?) {
            }

            override fun onSetFailure(p0: String?) {
            }

        }, constraints)
    }

    fun setLocalAudioStream() {
        Log.d(TAG, "startLocalVideo called ....")
        val localAudioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        val localAudioTrack =
            peerConnectionFactory.createAudioTrack("local_track_audio", localAudioSource)
        val localStream = peerConnectionFactory.createLocalMediaStream("local_stream")
        localStream.addTrack(localAudioTrack)
        peerConnection.addStream(localStream)
    }



}
