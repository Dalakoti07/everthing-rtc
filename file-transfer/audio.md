# Audio Call Extension Plan

The existing file-transfer app uses one PeerConnection that carries a DataChannel.
Adding audio is **additive** — the same PeerConnection carries an audio transceiver alongside the DataChannel.
No server changes are needed; the signaling server relays SDP/ICE unchanged regardless of what tracks the SDP describes.

---

## Files to change (5 total)

| File | Change |
|---|---|
| `AndroidManifest.xml` | Add `RECORD_AUDIO` permission |
| `WebRtcManager.kt` | `setUpAudio()`, fix `onAddStream`/`onAddTrack`, add `muteAudio`/`toggleSpeaker`/`endCall` |
| `MainStateHolder.kt` | `isMuted`, `isSpeakerOn` state fields; `MuteAudio`/`ToggleSpeaker`/`EndCall` actions |
| `MainViewModel.kt` | Handle 3 new actions |
| `MainActivity.kt` | Mute / Speaker / End buttons + `RECORD_AUDIO` runtime permission |

---

## 1. AndroidManifest.xml

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
```

---

## 2. WebRtcManager.kt

### New imports
```kotlin
import android.content.Context
import android.media.AudioManager
import org.webrtc.AudioTrack
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
```

### New fields
```kotlin
private val androidAudioManager =
    TransferApp.getContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
private var localAudioTrack: org.webrtc.MediaStreamTrack? = null
```

### `setUpAudio()` — call at the start of both `createOffer()` and `answerToOffer()`
```kotlin
private fun setUpAudio() {
    val constraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
    }
    val audioSource = peerConnectionFactory.createAudioSource(constraints)
    localAudioTrack = peerConnectionFactory.createAudioTrack("ARDAMSa0", audioSource)
    val transceiver = peerConnection.addTransceiver(localAudioTrack!!)
    transceiver.direction = RtpTransceiver.RtpTransceiverDirection.SEND_RECV
    localAudioTrack!!.setEnabled(true)
    (localAudioTrack as? AudioTrack)?.setVolume(1.0)
}
```

### Fix `onAddStream` and `onAddTrack` (currently empty stubs)
```kotlin
override fun onAddStream(mediaStream: MediaStream?) {
    // older WebRTC path — enable each incoming audio track
    mediaStream?.audioTracks?.forEach { it.setEnabled(true) }
}

override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
    super.onAddTrack(receiver, mediaStreams)
    mediaStreams?.forEach { stream ->
        stream.audioTracks.forEach { it.setEnabled(true) }
    }
    routeAudioToSpeaker()
}
```

### Audio routing and call controls
```kotlin
private fun routeAudioToSpeaker() {
    androidAudioManager.requestAudioFocus(
        null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
    )
    androidAudioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    androidAudioManager.isSpeakerphoneOn = true
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
```

### Update `createOffer()` and `answerToOffer()` MediaConstraints
```kotlin
// createOffer — add before peerConnection.createOffer(...)
setUpAudio()
val mediaConstraints = MediaConstraints().apply {
    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
}

// answerToOffer — add before peerConnection.createAnswer(...)
setUpAudio()
val constraints = MediaConstraints().apply {
    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
}
```

---

## 3. MainStateHolder.kt

```kotlin
// Add to MainScreenState:
val isMuted: Boolean = false,
val isSpeakerOn: Boolean = true,

// Add to MainActions:
data class MuteAudio(val mute: Boolean) : MainActions()
data class ToggleSpeaker(val on: Boolean) : MainActions()
data object EndCall : MainActions()
```

---

## 4. MainViewModel.kt

```kotlin
is MainActions.MuteAudio -> {
    rtcManager.muteAudio(actions.mute)
    _state.update { s -> s.copy(isMuted = actions.mute) }
}
is MainActions.ToggleSpeaker -> {
    rtcManager.toggleSpeaker(actions.on)
    _state.update { s -> s.copy(isSpeakerOn = actions.on) }
}
is MainActions.EndCall -> {
    rtcManager.endCall()
    _state.update { s ->
        s.copy(isRtcEstablished = false, peerConnectionString = "")
    }
}
```

---

## 5. MainActivity.kt

Add a runtime permission request in `MainScreen()`:
```kotlin
val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { /* handle denied */ }

LaunchedEffect(Unit) {
    permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
}
```

Add call-control buttons when `isRtcEstablished`:
```kotlin
// Mute toggle
Button(onClick = { dispatchAction(MainActions.MuteAudio(!state.isMuted)) }) {
    Text(if (state.isMuted) "Unmute" else "Mute")
}

// Speaker toggle
Button(onClick = { dispatchAction(MainActions.ToggleSpeaker(!state.isSpeakerOn)) }) {
    Text(if (state.isSpeakerOn) "Earpiece" else "Speaker")
}

// End call
Button(
    onClick = { dispatchAction(MainActions.EndCall) },
    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
) { Text("End") }
```

---

## Why no server changes are needed

The WebSocket signaling server relays SDP and ICE candidates as opaque JSON blobs.
Adding audio transceivers only changes the *content* of the SDP (adds an `m=audio` line),
but the server forwards it through unchanged. The DataChannel `m=application` line is already there.
Both coexist on the same PeerConnection without conflict.
