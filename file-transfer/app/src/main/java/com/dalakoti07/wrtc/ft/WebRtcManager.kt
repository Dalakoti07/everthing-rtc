package com.dalakoti07.wrtc.ft

import android.util.Log
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

private const val TAG = "WebRtcManager"

class WebRTCManager(
    private val socketConnection: SocketConnection,
    private val userName: String,
): PeerConnection.Observer {
    private var target: String = "TARGET-XX"

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
    private lateinit var remoteDataChannel: DataChannel

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
            .builder(TransferApp.getContext())
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val peerConnectionFactoryOptions = PeerConnectionFactory.Options()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(peerConnectionFactoryOptions)
            .createPeerConnectionFactory()
    }

    private fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        peerConnection =
            peerConnectionFactory.createPeerConnection(rtcConfig, this)!!
    }

    private fun createDataChannel(label: String) {
        val init = DataChannel.Init()
        dataChannel = peerConnection.createDataChannel(label, init)
        dataChannel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(amount: Long) {}
            override fun onStateChange() {}
            override fun onMessage(buffer: DataChannel.Buffer?) {}
        })
    }

    fun createOffer(from: String, target: String){
        Log.d(TAG, "user is available creating offer")
        val sdpObserver = object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(desc: SessionDescription?) {
                        Log.d(TAG, "onCreateSuccess: .... using socket to notify peer")
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
                    override fun onSetSuccess() {
                        Log.d(TAG, "onSetSuccess: ")
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
        peerConnection.createOffer(sdpObserver, mediaConstraints)
    }

    fun addIceCandidate(p0: IceCandidate?) {
        peerConnection.addIceCandidate(p0)
    }

    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
    }

    override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
    }

    override fun onIceConnectionReceivingChange(p0: Boolean) {
    }

    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
    }

    override fun onIceCandidate(p0: IceCandidate?) {
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

    override fun onAddStream(p0: MediaStream?) {
    }

    override fun onRemoveStream(p0: MediaStream?) {
    }

    override fun onDataChannel(p0: DataChannel?) {
        remoteDataChannel = p0!!
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

}
