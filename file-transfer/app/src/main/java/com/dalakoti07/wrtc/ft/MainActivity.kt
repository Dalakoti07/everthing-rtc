package com.dalakoti07.wrtc.ft

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dalakoti07.wrtc.ft.ui.theme.FiletransferappTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FiletransferappTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun <T> rememberFlowWithLifecycle(
    flow: Flow<T>,
    lifecycle: Lifecycle = LocalLifecycleOwner.current.lifecycle,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
): Flow<T> = remember(flow, lifecycle, minActiveState) {
    flow.flowWithLifecycle(lifecycle = lifecycle, minActiveState = minActiveState)
}

@Composable
fun MainScreen() {
    val viewModel = viewModel(modelClass = MainViewModel::class.java)
    val state by viewModel.state.collectAsState()
    val events = rememberFlowWithLifecycle(flow = viewModel.oneTimeEvents)

    var showIncomingDialog by remember { mutableStateOf(false) }

    // Request RECORD_AUDIO on first composition
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) android.util.Log.w("MainActivity", "RECORD_AUDIO denied")
    }
    LaunchedEffect(Unit) {
        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(events) {
        events.collectLatest {
            when (it) {
                is MainOneTimeEvents.GotInvite -> showIncomingDialog = true
            }
        }
    }

    if (showIncomingDialog) {
        IncomingCallDialog(
            caller = state.inComingRequestFrom,
            onAccept = {
                viewModel.dispatchAction(MainActions.AcceptIncomingConnection)
                showIncomingDialog = false
            },
            onDecline = { showIncomingDialog = false },
        )
    }

    if (state.isRtcEstablished) {
        InCallScreen(state = state, dispatchAction = { viewModel.dispatchAction(it) })
    } else {
        ConnectScreen(state = state, dispatchAction = { viewModel.dispatchAction(it) })
    }
}

// ── Connect screen ────────────────────────────────────────────────────────────

@Composable
fun ConnectScreen(
    state: MainScreenState,
    dispatchAction: (MainActions) -> Unit,
) {
    var nameInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFEFDED))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (state.isConnectedToServer) "Connected as ${state.connectedAs}"
                   else "WebRTC Audio Call",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFD20062),
        )

        Spacer(Modifier.height(32.dp))

        TextField(
            value = nameInput,
            onValueChange = { nameInput = it },
            placeholder = {
                Text(if (state.isConnectedToServer) "Enter peer name" else "Enter your name")
            },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = Color(0xFFFA7070).copy(alpha = 0.3f),
                unfocusedContainerColor = Color(0xFFFA7070).copy(alpha = 0.15f),
            ),
            shape = RoundedCornerShape(12.dp),
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                if (nameInput.isNotBlank()) {
                    if (state.isConnectedToServer) {
                        dispatchAction(MainActions.ConnectToUser(nameInput))
                    } else {
                        dispatchAction(MainActions.ConnectAs(nameInput))
                    }
                    nameInput = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFD20062),
                contentColor = Color.White,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = if (state.isConnectedToServer) "Call" else "Join",
                fontSize = 16.sp,
            )
        }
    }
}

// ── In-call screen ────────────────────────────────────────────────────────────

@Composable
fun InCallScreen(
    state: MainScreenState,
    dispatchAction: (MainActions) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .padding(32.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Status
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Text(text = "●  On call", color = Color(0xFF4CAF50), fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = state.connectedToPeer.ifEmpty { "Peer" },
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            CallControlButton(
                label = if (state.isMuted) "Unmute" else "Mute",
                color = if (state.isMuted) Color(0xFF555555) else Color(0xFF333355),
                onClick = { dispatchAction(MainActions.MuteAudio(!state.isMuted)) },
            )
            CallControlButton(
                label = "End",
                color = Color(0xFFCC0000),
                onClick = { dispatchAction(MainActions.EndCall) },
            )
            CallControlButton(
                label = if (state.isSpeakerOn) "Earpiece" else "Speaker",
                color = Color(0xFF333355),
                onClick = { dispatchAction(MainActions.ToggleSpeaker(!state.isSpeakerOn)) },
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun CallControlButton(label: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(text = label, fontSize = 14.sp)
    }
}

// ── Incoming call dialog ──────────────────────────────────────────────────────

@Composable
fun IncomingCallDialog(caller: String, onAccept: () -> Unit, onDecline: () -> Unit) {
    Dialog(onDismissRequest = onDecline) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Incoming call from",
                fontSize = 14.sp,
                color = Color.Gray,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = caller,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A2E),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Button(
                    onClick = onDecline,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC0000)),
                ) { Text("Decline", color = Color.White) }

                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                ) { Text("Accept", color = Color.White) }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ConnectScreenPreview() {
    FiletransferappTheme {
        ConnectScreen(state = MainScreenState(isConnectedToServer = true, connectedAs = "P6"), dispatchAction = {})
    }
}

@Preview(showBackground = true)
@Composable
fun InCallScreenPreview() {
    FiletransferappTheme {
        InCallScreen(
            state = MainScreenState(isRtcEstablished = true, connectedToPeer = "Moto", isMuted = false, isSpeakerOn = true),
            dispatchAction = {},
        )
    }
}
