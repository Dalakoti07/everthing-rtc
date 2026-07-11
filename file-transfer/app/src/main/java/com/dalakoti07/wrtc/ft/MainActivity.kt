package com.dalakoti07.wrtc.ft

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dalakoti07.wrtc.ft.rtc.MessageType
import com.dalakoti07.wrtc.ft.ui.theme.FiletransferappTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

private const val TAG = "MainActivity"

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

    var showIncomingRequestDialog by remember { mutableStateOf(false) }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.dispatchAction(MainActions.SendFile(it)) }
    }

    LaunchedEffect(key1 = events) {
        events.collectLatest {
            when (it) {
                is MainOneTimeEvents.GotInvite -> showIncomingRequestDialog = true
            }
        }
    }

    if (showIncomingRequestDialog) {
        DialogForIncomingRequest(
            onDismiss = { showIncomingRequestDialog = false },
            onAccept = {
                viewModel.dispatchAction(MainActions.AcceptIncomingConnection)
                showIncomingRequestDialog = false
            },
            inviteFrom = state.inComingRequestFrom,
        )
    }

    HomeScreenContent(
        state = state,
        onPickFile = { fileLauncher.launch("*/*") },
        dispatchAction = { viewModel.dispatchAction(it) },
    )
}

@Composable
fun HomeScreenContent(
    state: MainScreenState,
    onPickFile: () -> Unit = {},
    dispatchAction: (MainActions) -> Unit = {},
) {
    var yourName by remember { mutableStateOf("") }
    var connectTo by remember { mutableStateOf("") }
    var chatMessage by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(state.messagesFromServer.size) {
        if (state.messagesFromServer.isNotEmpty()) {
            listState.animateScrollToItem(state.messagesFromServer.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color(0xFFFEFDED)),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(bottom = 130.dp),
            content = {
                item {
                    Text(
                        text = if (state.peerConnectionString.isNotEmpty()) state.peerConnectionString
                        else if (state.isConnectedToServer) "Connected as ${state.connectedAs}"
                        else "Not connected to server",
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(color = Color(0xFFD20062))
                            .padding(10.dp),
                        color = Color.White,
                    )
                }

                items(state.messagesFromServer.size) { index ->
                    when (val current = state.messagesFromServer[index]) {
                        is MessageType.Info -> Text(
                            text = current.msg,
                            modifier = Modifier
                                .padding(top = 10.dp, start = 10.dp)
                                .fillMaxWidth(),
                            fontSize = 13.sp,
                            color = Color.Gray,
                        )
                        is MessageType.MessageByMe -> Row(
                            modifier = Modifier
                                .padding(top = 10.dp, start = 10.dp)
                                .fillMaxWidth(),
                        ) {
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = current.msg,
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color(0xFF240A34), RoundedCornerShape(10.dp))
                                    .padding(8.dp),
                                color = Color.White,
                            )
                        }
                        is MessageType.MessageByPeer -> Row(
                            modifier = Modifier
                                .padding(top = 10.dp, start = 10.dp)
                                .fillMaxWidth(),
                        ) {
                            Text(
                                text = current.msg,
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color(0xFFFA7070), RoundedCornerShape(10.dp))
                                    .padding(8.dp),
                                color = Color.White,
                            )
                            Spacer(Modifier.weight(1f))
                        }
                        else -> {}
                    }
                }
            },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xFFFEFDED))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            // ── File transfer progress bars ────────────────────────────
            if (state.isRtcEstablished) {
                if (state.sendProgress > 0f) {
                    TransferProgressRow(
                        label = if (state.sendProgress < 1f)
                            "Sending ${state.sendingFileName}"
                        else "Sent ${state.sendingFileName}",
                        progress = state.sendProgress,
                        color = Color(0xFF240A34),
                    )
                    Spacer(Modifier.height(4.dp))
                }

                if (state.receiveProgress > 0f) {
                    TransferProgressRow(
                        label = if (state.receiveProgress < 1f)
                            "Receiving ${state.receivingFileName}"
                        else "Received ${state.receivingFileName}",
                        progress = state.receiveProgress,
                        color = Color(0xFFD20062),
                    )
                    state.receivedFilePath?.let { path ->
                        Text(
                            text = "Saved: $path",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            // ── Input row ─────────────────────────────────────────────
            if (state.isRtcEstablished) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextField(
                        modifier = Modifier.weight(1f),
                        value = chatMessage,
                        onValueChange = { chatMessage = it },
                        placeholder = { Text("Message") },
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = Color(0xFFFA7070),
                            unfocusedContainerColor = Color(0xFFFA7070),
                        ),
                        shape = RoundedCornerShape(15.dp),
                    )
                    Button(
                        onClick = {
                            if (chatMessage.isNotBlank()) {
                                dispatchAction(MainActions.SendChatMessage(chatMessage))
                                chatMessage = ""
                            }
                        },
                        modifier = Modifier.padding(start = 6.dp),
                        colors = ButtonDefaults.buttonColors(
                            contentColor = Color.White,
                            containerColor = Color(0xFFFA7070),
                        ),
                    ) { Text("Send") }

                    Button(
                        onClick = onPickFile,
                        modifier = Modifier.padding(start = 6.dp),
                        colors = ButtonDefaults.buttonColors(
                            contentColor = Color.White,
                            containerColor = Color(0xFF240A34),
                        ),
                    ) { Text("File") }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextField(
                        modifier = Modifier.weight(1f),
                        value = if (state.connectedAs.isNotEmpty()) connectTo else yourName,
                        onValueChange = {
                            if (state.connectedAs.isNotEmpty()) connectTo = it else yourName = it
                        },
                        placeholder = {
                            Text(if (state.connectedAs.isNotEmpty()) "Peer name" else "Your name")
                        },
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = Color(0xFFFA7070),
                            unfocusedContainerColor = Color(0xFFFA7070),
                        ),
                        shape = RoundedCornerShape(15.dp),
                    )
                    Button(
                        onClick = {
                            if (state.connectedAs.isNotEmpty()) {
                                dispatchAction(MainActions.ConnectToUser(connectTo))
                            } else {
                                dispatchAction(MainActions.ConnectAs(yourName))
                            }
                        },
                        modifier = Modifier.padding(start = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            contentColor = Color.White,
                            containerColor = Color(0xFFFA7070),
                        ),
                    ) { Text("GO") }
                }
            }
        }
    }
}

@Composable
fun TransferProgressRow(label: String, progress: Float, color: Color) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, fontSize = 12.sp, color = color)
            Text(
                text = "${(progress * 100).toInt()}%",
                fontSize = 12.sp,
                color = color,
            )
        }
        Spacer(Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = color,
            trackColor = color.copy(alpha = 0.2f),
        )
    }
}

@Preview
@Composable
fun DialogForIncomingRequestPreview() {
    FiletransferappTheme {
        DialogForIncomingRequest(onAccept = {}, onDismiss = {}, inviteFrom = "Shah Rukh Khan")
    }
}

@Composable
fun DialogForIncomingRequest(
    onDismiss: () -> Unit = {},
    onAccept: () -> Unit = {},
    inviteFrom: String,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = Color.White)
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "File transfer request from $inviteFrom")
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.padding(vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        contentColor = Color.White,
                        containerColor = Color(0xFFFA7070),
                    ),
                ) { Text("Cancel") }

                Button(
                    onClick = onAccept,
                    modifier = Modifier.padding(vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        contentColor = Color.White,
                        containerColor = Color(0xFFFA7070),
                    ),
                ) { Text("Accept") }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    FiletransferappTheme {
        val state = remember { MainScreenState.forPreview() }
        HomeScreenContent(state = state)
    }
}
