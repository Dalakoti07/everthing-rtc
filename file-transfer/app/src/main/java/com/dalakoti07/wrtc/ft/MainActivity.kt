package com.dalakoti07.wrtc.ft

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
    var enteredName by remember {
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
        Row(
            modifier = Modifier.align(
                Alignment.BottomCenter,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                modifier = Modifier.weight(1f),
                value = enteredName,
                onValueChange = {
                    enteredName = it
                },
            )
            Button(
                onClick = {
                    viewModel.dispatchAction(
                        MainActions.ConnectAs(enteredName)
                    )
                },
                modifier = Modifier.padding(
                    start = 10.dp,
                    end = 10.dp,
                ),
            ) {
                Text(text = "Send")
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