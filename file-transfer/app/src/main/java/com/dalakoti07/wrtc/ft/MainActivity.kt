package com.dalakoti07.wrtc.ft

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dalakoti07.wrtc.ft.ui.theme.FiletransferappTheme

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
fun MainScreen() {
    //todo saurabh rename them to yourName and peer's Name
    var enteredName by remember {
        mutableStateOf("")
    }
    var connectTo by remember {
        mutableStateOf("")
    }
    var chatMessage by remember {
        mutableStateOf("")
    }
    val viewModel = viewModel(modelClass = MainViewModel::class.java)
    val state = viewModel.state.collectAsState()
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Text(
            text = if (state.value.isConnectedToServer)
                "Connected to server as ${state.value.connectedAs}"
            else "Not connected",
            modifier = Modifier
                .align(
                    Alignment.TopCenter,
                )
                .fillMaxWidth()
                .background(
                    color = if (state.value.isConnectedToServer)
                        Color.Green
                    else Color.Red
                )
                .padding(
                    10.dp
                ),
        )
        LazyColumn(
            content = {
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
                items(state.value.messagesFromServer.size) {
                    Text(
                        text = state.value.messagesFromServer[it],
                        modifier = Modifier
                            .padding(
                                top = 10.dp,
                            )
                            .fillMaxWidth()
                    )
                }
            },
        )
        Column(
            modifier = Modifier.align(
                Alignment.BottomCenter,
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    modifier = Modifier.weight(1f),
                    value = if (state.value.connectedAs.isNotEmpty()) {
                        connectTo
                    } else {
                        enteredName
                    },
                    onValueChange = {
                        if (state.value.connectedAs.isNotEmpty()) {
                            connectTo = it
                        } else {
                            enteredName = it
                        }
                    },
                )
                Button(
                    onClick = {
                        if (state.value.connectedAs.isNotEmpty()) {
                            viewModel.dispatchAction(
                                MainActions.ConnectToUser(connectTo)
                            )
                        } else {
                            viewModel.dispatchAction(
                                MainActions.ConnectAs(enteredName)
                            )
                        }
                    },
                    modifier = Modifier.padding(
                        start = 10.dp,
                        end = 10.dp,
                    ),
                ) {
                    Text(text = "GO")
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(
                    top = 10.dp,
                ),
            ) {
                TextField(
                    modifier = Modifier.weight(1f),
                    value = chatMessage,
                    onValueChange = {
                        chatMessage = it
                    },
                )
                Button(
                    onClick = {
                        viewModel.dispatchAction(
                            MainActions.SendChatMessage(chatMessage)
                        )
                    },
                    modifier = Modifier.padding(
                        start = 10.dp,
                        end = 10.dp,
                    ),
                ) {
                    Text(text = "Chat")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    FiletransferappTheme {
        MainScreen()
    }
}