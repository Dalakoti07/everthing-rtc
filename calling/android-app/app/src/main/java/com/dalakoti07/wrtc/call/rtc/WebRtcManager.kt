package com.dalakoti07.wrtc.call.rtc

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.dalakoti07.wrtc.call.CallApp
import com.dalakoti07.wrtc.call.socket.MessageModel
import com.dalakoti07.wrtc.call.socket.SocketConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.IceConnectionState
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import kotlin.math.log

private const val TAG = "WebRtcManager"

sealed class MessageType {
    data class Info(val msg: String) : MessageType()
    data object ConnectedToPeer : MessageType()
    data object DisconnectFromPeer: MessageType()
}

class WebRTCManager(
    private var target: String,
    private val socketConnection: SocketConnection,
    private val userName: String,
    private val isCaller: Boolean,
) : PeerConnection.Observer {
    private val audioManager = CallApp.getContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager

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
    lateinit var peerConnectionFactory: PeerConnectionFactory
    lateinit var peerConnection: PeerConnection
    private lateinit var dataChannel: DataChannel
    private lateinit var audioTrack: MediaStreamTrack

    init {
        initializePeerConnectionFactory()
        createPeerConnection()
    }

    fun updateTarget(name: String) {
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

    private fun setUpAudio() {
        val audioConstraints = MediaConstraints()
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))

        val audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        audioTrack = peerConnectionFactory.createAudioTrack("ARDAMSa0", audioSource)
        val transceiver = peerConnection.addTransceiver(audioTrack)
        transceiver.direction = RtpTransceiver.RtpTransceiverDirection.SEND_RECV

        audioTrack.setEnabled(true)
        (audioTrack as AudioTrack?)?.setVolume(1.0)
    }

    fun createOffer(from: String, target: String) {
        setUpAudio()
        Log.d(TAG, "user is available creating offer")
        val sdpObserver = object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(desc: SessionDescription?) {
                        Log.d(TAG, "onCreateSuccess: .... using socket to notify peer")
                    }

                    override fun onSetSuccess() {
                        Log.d(TAG, "onSetSuccess: offer -> ${desc?.description}")
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
                        Log.e(TAG, "error in creating offer $error")
                    }

                    override fun onSetFailure(error: String?) {
                        Log.e(TAG, "onSetFailure: err-> $error")
                    }
                }, desc)
                // Send offer to signaling server
                // Signaling server will forward it to the other peer
                // Upon receiving answer, set it as remote description
            }

            override fun onSetSuccess() {
                Log.d(TAG, "createOffer onSetSuccess: ")
            }
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "createOffer onCreateFailure: ", )
            }
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "createOffer onSetFailure: ", )
            }
        }

        val mediaConstraints = MediaConstraints()
        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        peerConnection.createOffer(sdpObserver, mediaConstraints)
    }

    fun addIceCandidate(p0: IceCandidate?) {
        peerConnection.addIceCandidate(p0)
    }

    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
    }

    override fun onIceConnectionChange(newState: IceConnectionState?) {
        when (newState) {
            IceConnectionState.CONNECTED,
            IceConnectionState.COMPLETED -> {
                // Peers are connected
                Log.d(TAG, "ICE Connection State: Connected ")
                scope.launch {
                    _messageStream.emit(
                        MessageType.ConnectedToPeer
                    )
                }
            }
            IceConnectionState.DISCONNECTED, IceConnectionState.CLOSED->{
                Log.d(TAG, "ICE Connection State: DISCONNECTED or CLOSED")
                scope.launch {
                    _messageStream.emit(
                        MessageType.DisconnectFromPeer
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
            MessageModel("ice_candidate", userName, target, candidate)
        )
    }

    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
        Log.d(TAG, "onIceCandidatesRemoved: ")
    }

    override fun onAddStream(mediaStream: MediaStream?) {
        Log.d(TAG, "onAddStream: called -> ${mediaStream?.audioTracks?.size}")
        mediaStream?.audioTracks?.forEach { audioTrack ->
            Log.d(TAG, "onAddStream: track id -> ${audioTrack.id()}")
            audioTrack.setEnabled(true)
        }
    }

    override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
        super.onAddTrack(receiver, mediaStreams)
        Log.d(TAG, "onAddTrack: invoked media size -> ${mediaStreams?.size}")
        //routeAudioToSpeaker()
        mediaStreams?.forEach { mediaStream ->
            mediaStream.audioTracks.forEach { audioTrack ->
                audioTrack.setEnabled(true)
                Log.d(TAG, "onAddTrack: track -> ${audioTrack.id()}")
            }
        }
    }

    private fun routeAudioToSpeaker() {
        // Request audio focus for playback
        audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        // Set mode to MODE_IN_COMMUNICATION as recommended for VoIP
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        // Force audio to come out of the speaker
        audioManager.isSpeakerphoneOn = true
    }

    fun restoreAudioSettings(context: Context) {
        audioManager.isSpeakerphoneOn = false
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.abandonAudioFocus(null)
    }

    override fun onTrack(transceiver: RtpTransceiver?) {
        super.onTrack(transceiver)
        Log.d(TAG, "onTrack: invoked")

    }

    override fun onRemoveTrack(receiver: RtpReceiver?) {
        super.onRemoveTrack(receiver)
        Log.d(TAG, "onRemoveTrack: invoked")
    }

    override fun onRemoveStream(p0: MediaStream?) {
        Log.d(TAG, "onRemoveStream: ...")
    }

    override fun onDataChannel(p0: DataChannel?) {
        Log.d(TAG, "onDataChannel: called for peers")
        p0!!.registerObserver(object : DataChannel.Observer {
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
        Log.d(TAG, "onRenegotiationNeeded: ....")
    }

    fun onRemoteSessionReceived(session: SessionDescription) {
        peerConnection.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {
                Log.d(TAG, "onRemoteSessionReceived onCreateSuccess: ")
            }

            override fun onSetSuccess() {
                Log.d(TAG, "onRemoteSessionReceived onSetSuccess: ")
            }

            override fun onCreateFailure(p0: String?) {
                Log.e(TAG, "onRemoteSessionReceived onCreateFailure: ", )
            }

            override fun onSetFailure(p0: String?) {
                Log.e(TAG, "onRemoteSessionReceived onSetFailure: ", )
            }

        }, session)
    }

    fun answerToOffer(lTarget: String?) {
        setUpAudio()
        checkTransceivers()
        val mediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        peerConnection.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {
                        Log.d(TAG, "answerToOffer onCreateSuccess: $p0")
                    }

                    override fun onSetSuccess() {
                        // this does not contains a=sendrcv
                        Log.d(TAG, "answerToOffer onSetSuccess: answer ${desc?.description}")
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
                        Log.e(TAG, "onCreateFailure: $p0")
                    }

                    override fun onSetFailure(p0: String?) {
                        Log.e(TAG, "onSetFailure: $p0", )
                    }

                }, desc)
            }

            override fun onSetSuccess() {
                Log.d(TAG, "answerToOffer onSetSuccess: ")
            }

            override fun onCreateFailure(p0: String?) {
                Log.e(TAG, "answerToOffer onCreateFailure: ", )
            }

            override fun onSetFailure(p0: String?) {
                Log.e(TAG, "answerToOffer onSetFailure: ", )
            }

        }, mediaConstraints)
    }

    private fun checkTransceivers() {
        Log.d(TAG, "checkTransceivers: before manipulation ")
        peerConnection.transceivers.forEach {
            Log.d(TAG, "Transceiver: track=${it.receiver.track()?.id()} kind=${it.receiver.track()?.kind()} direction=${it.direction}")
        }
        // Check if a transceiver for this track already exists and set it to SEND_RECV
        val transceiver = peerConnection.transceivers.find {
            it.receiver.track()?.id() == audioTrack.id()
        } ?: peerConnection.addTransceiver(audioTrack)

        transceiver.setDirection(RtpTransceiver.RtpTransceiverDirection.SEND_RECV)
        Log.d(TAG, "checkTransceivers: again list")
        peerConnection.transceivers.forEach {
            Log.d(TAG, "Transceiver: track=${it.receiver.track()?.id()} kind=${it.receiver.track()?.kind()} direction=${it.direction}")
        }
    }


}
