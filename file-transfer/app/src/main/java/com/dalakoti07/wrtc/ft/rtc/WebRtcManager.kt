package com.dalakoti07.wrtc.ft.rtc

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.dalakoti07.wrtc.ft.TransferApp
import com.dalakoti07.wrtc.ft.socket.MessageModel
import com.dalakoti07.wrtc.ft.socket.SocketConnection
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
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

private const val TAG = "WebRtcManager"

sealed class MessageType {
    data class Info(val msg: String) : MessageType()
    data object ConnectedToPeer : MessageType()
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

    private val androidAudioManager =
        TransferApp.getContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var localAudioTrack: org.webrtc.MediaStreamTrack? = null

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:iphone-stun.strato-iphone.de:3478").createIceServer(),
        PeerConnection.IceServer("stun:openrelay.metered.ca:80"),
        PeerConnection.IceServer("turn:openrelay.metered.ca:80", "openrelayproject", "openrelayproject"),
        PeerConnection.IceServer("turn:openrelay.metered.ca:443", "openrelayproject", "openrelayproject"),
        PeerConnection.IceServer("turn:openrelay.metered.ca:443?transport=tcp", "openrelayproject", "openrelayproject"),
    )

    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private lateinit var peerConnection: PeerConnection

    init {
        initializePeerConnectionFactory()
        createPeerConnection()
    }

    fun updateTarget(name: String) { target = name }

    private fun initializePeerConnectionFactory() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions
                .builder(TransferApp.getContext())
                .createInitializationOptions()
        )
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

    private fun createPeerConnection() {
        peerConnection = peerConnectionFactory.createPeerConnection(iceServers, this)!!
    }

    // ── Audio ─────────────────────────────────────────────────────────────────

    private fun setUpAudio() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        val audioSource = peerConnectionFactory.createAudioSource(constraints)
        localAudioTrack = peerConnectionFactory.createAudioTrack("ARDAMSa0", audioSource)
        localAudioTrack!!.setEnabled(true)
        (localAudioTrack as? AudioTrack)?.setVolume(1.0)

        // addTrack is spec-compliant for both caller and answerer:
        // - Caller path (no remote desc yet): creates a new sendrecv transceiver → offer has m=audio sendrecv
        // - Answerer path (remote offer already applied): WebRTC auto-created a recvonly transceiver
        //   when the offer was parsed; addTrack recycles it, upgrades direction to sendrecv,
        //   and attaches our mic track to its sender — all without adding a second m=audio line.
        peerConnection.addTrack(localAudioTrack!!, emptyList())
        Log.d(TAG, "setUpAudio: track added, transceivers=${peerConnection.transceivers.size}")
    }

    private fun routeAudioToSpeaker() {
        androidAudioManager.requestAudioFocus(
            null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        )
        androidAudioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        androidAudioManager.isSpeakerphoneOn = true
        // Ensure call volume is not zero — MODE_IN_COMMUNICATION can reset it
        val max = androidAudioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
        androidAudioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, max, 0)
    }

    fun muteAudio(mute: Boolean) {
        localAudioTrack?.setEnabled(!mute)
    }

    fun toggleSpeaker(on: Boolean) {
        androidAudioManager.isSpeakerphoneOn = on
    }

    fun endCall() {
        localAudioTrack?.setEnabled(false)
        androidAudioManager.isSpeakerphoneOn = false
        androidAudioManager.mode = AudioManager.MODE_NORMAL
        androidAudioManager.abandonAudioFocus(null)
        peerConnection.close()
    }

    // ── Offer / Answer ────────────────────────────────────────────────────────

    fun createOffer(from: String, target: String) {
        setUpAudio()
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        peerConnection.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        socketConnection.sendMessageToSocket(
                            MessageModel("create_offer", from, target,
                                hashMapOf("sdp" to desc?.description, "type" to desc?.type))
                        )
                    }
                    override fun onCreateFailure(e: String?) { Log.e(TAG, "offer create fail: $e") }
                    override fun onSetFailure(e: String?) { Log.e(TAG, "offer set fail: $e") }
                }, desc)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(e: String?) {}
            override fun onSetFailure(e: String?) {}
        }, constraints)
    }

    fun answerToOffer(lTarget: String?) {
        setUpAudio()
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        peerConnection.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        socketConnection.sendMessageToSocket(
                            MessageModel("create_answer", userName, lTarget,
                                hashMapOf("sdp" to desc?.description, "type" to desc?.type))
                        )
                    }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {}
                }, desc)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    fun addIceCandidate(p0: IceCandidate?) { peerConnection.addIceCandidate(p0) }

    fun onRemoteSessionReceived(session: SessionDescription) {
        peerConnection.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, session)
    }

    // ── PeerConnection.Observer ───────────────────────────────────────────────

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
        addIceCandidate(p0)
        socketConnection.sendMessageToSocket(
            MessageModel("ice_candidate", userName, target,
                hashMapOf("sdpMid" to p0?.sdpMid, "sdpMLineIndex" to p0?.sdpMLineIndex, "sdpCandidate" to p0?.sdp))
        )
    }

    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}

    override fun onAddStream(mediaStream: MediaStream?) {
        Log.d(TAG, "onAddStream: ${mediaStream?.audioTracks?.size} audio tracks")
        mediaStream?.audioTracks?.forEach { it.setEnabled(true) }
    }

    override fun onRemoveStream(p0: MediaStream?) {}

    // mediaStreams is empty when the remote peer used addTrack() without a stream,
    // so enable the remote track directly from receiver instead.
    override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
        super.onAddTrack(receiver, mediaStreams)
        val track = receiver?.track()
        Log.d(TAG, "onAddTrack: kind=${track?.kind()} id=${track?.id()}")
        track?.setEnabled(true)
        routeAudioToSpeaker()
    }

    override fun onDataChannel(p0: DataChannel?) {}
    override fun onRenegotiationNeeded() {}
}
