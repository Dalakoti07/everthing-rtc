package com.dalakoti07.wrtc.call.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver

class AudioRecorder(
    private val peerConnection: PeerConnection,
    private val peerConnectionFactory: PeerConnectionFactory,
) {
    // Define audio parameters
    private val sampleRate = 16000 // Sample rate in Hz
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO // Mono channel
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT // 16-bit PCM

    /*
    // Create AudioRecord object to capture audio from microphone
    @SuppressLint("MissingPermission")
    private val audioRecord = AudioRecord(
        MediaRecorder.AudioSource.MIC, // Audio source: microphone
        sampleRate,
        channelConfig,
        audioFormat,
        bufferSize
    )*/

    fun startRecordingAndEmit(){
        // Create AudioTrack with encoded audio data


        // Add local MediaStream to PeerConnection
        //peerConnection.addTrack(audioTrack)
    }



}