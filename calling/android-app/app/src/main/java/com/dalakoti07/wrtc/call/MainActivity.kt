package com.dalakoti07.wrtc.call

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dalakoti07.wrtc.call.rtc.MessageType
import com.dalakoti07.wrtc.call.ui.theme.CallAppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CallAppTheme {
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
    flow.flowWithLifecycle(
        lifecycle = lifecycle,
        minActiveState = minActiveState,
    )
}

@Composable
fun MainScreen() {
    val viewModel = viewModel(modelClass = MainViewModel::class.java)
    val state by viewModel.state.collectAsState()
    val events = rememberFlowWithLifecycle(flow = viewModel.oneTimeEvents)
    var showIncomingRequestDialog by remember {
        mutableStateOf(false)
    }
    LaunchedEffect(
        key1 = events,
        block = {
            events.collectLatest {
                when (it) {
                    is MainOneTimeEvents.GotInvite -> {
                        showIncomingRequestDialog = true
                    }
                }
            }
        },
    )
    if (showIncomingRequestDialog) {
        DialogForIncomingRequest(
            onDismiss = {
                showIncomingRequestDialog = false
            },
            onAccept = {
                viewModel.dispatchAction(
                    MainActions.AcceptIncomingConnection
                )
                showIncomingRequestDialog = false
            },
            inviteFrom = state.inComingRequestFrom,
        )
    }
    HomeScreenContent(
        state = state,
        dispatchAction = {
            viewModel.dispatchAction(
                it
            )
        },
    )
}

@Composable
fun HomeScreenContent(
    state: MainScreenState,
    dispatchAction: (MainActions) -> Unit = {},
) {
    var yourName by remember {
        mutableStateOf("")
    }
    var connectTo by remember {
        mutableStateOf("")
    }
    var chatMessage by remember {
        mutableStateOf("")
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = Color(0xFFFEFDED)
            ),
    ) {
        LazyColumn(
            content = {
                item {
                    if (state.peerConnectionString.isEmpty()) {
                        Text(
                            text = if (state.isConnectedToServer)
                                "Connected to server as ${state.connectedAs}"
                            else "Not connected to server",
                            modifier = Modifier
                                .align(
                                    Alignment.TopCenter,
                                )
                                .fillMaxWidth()
                                .background(
                                    color = Color(0xFFD20062),
                                )
                                .padding(
                                    10.dp
                                ),
                            color = Color.White,
                        )
                    } else {
                        Text(
                            text = state.peerConnectionString,
                            modifier = Modifier
                                .align(
                                    Alignment.TopCenter,
                                )
                                .fillMaxWidth()
                                .background(
                                    Color(0xFFD20062),
                                )
                                .padding(
                                    10.dp
                                ),
                            color = Color.White,
                        )
                    }
                }
                items(state.messagesFromServer.size) {
                    val current = state.messagesFromServer[it]
                    when (current) {
                        is MessageType.Info -> {
                            Text(
                                text = current.msg,
                                modifier = Modifier
                                    .padding(
                                        top = 10.dp,
                                        start = 10.dp,
                                    )
                                    .fillMaxWidth()
                            )
                        }

                        else -> {}
                    }
                }
            },
        )
        Column(
            modifier = Modifier.align(
                Alignment.BottomCenter,
            ),
        ) {
            if (!state.isRtcEstablished) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(
                        start = 10.dp,
                    ),
                ) {
                    TextField(
                        modifier = Modifier.weight(1f),
                        value = if (state.connectedAs.isNotEmpty()) {
                            connectTo
                        } else {
                            yourName
                        },
                        onValueChange = {
                            if (state.connectedAs.isNotEmpty()) {
                                connectTo = it
                            } else {
                                yourName = it
                            }
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
                                dispatchAction(
                                    MainActions.ConnectToUser(connectTo)
                                )
                            } else {
                                dispatchAction(
                                    MainActions.ConnectAs(yourName)
                                )
                            }
                        },
                        modifier = Modifier.padding(
                            start = 10.dp,
                            end = 10.dp,
                        ),
                        colors = ButtonDefaults.buttonColors(
                            contentColor = Color.White,
                            containerColor = Color(0xFFFA7070),
                        ),
                    ) {
                        Text(text = "GO")
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun DialogForIncomingRequestPreview() {
    CallAppTheme {
        DialogForIncomingRequest(
            onAccept = {},
            onDismiss = {},
            inviteFrom = "Shah Rukh Khan"
        )
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
                .background(
                    color = Color.White,
                )
                .padding(
                    8.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "You got invite from $inviteFrom")
            Row(
                modifier = Modifier
                    .padding(
                        horizontal = 20.dp,
                    )
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.padding(
                        vertical = 10.dp,
                    ),
                    colors = ButtonDefaults.buttonColors(
                        contentColor = Color.White,
                        containerColor = Color(0xFFFA7070),
                    ),
                ) {
                    Text(text = "Cancel")
                }
                Button(
                    onClick = onAccept,
                    modifier = Modifier.padding(
                        vertical = 10.dp,
                    ),
                    colors = ButtonDefaults.buttonColors(
                        contentColor = Color.White,
                        containerColor = Color(0xFFFA7070),
                    ),
                ) {
                    Text(text = "Accept")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CallAppTheme {
        val state by remember {
            mutableStateOf(MainScreenState.forPreview())
        }
        HomeScreenContent(
            state = state,
        )
    }
}
