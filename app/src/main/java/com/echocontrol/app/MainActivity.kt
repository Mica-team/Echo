package com.echocontrol.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.echocontrol.app.auth.AuthenticationScreen
import com.echocontrol.app.navigation.AppNavHost
import com.echocontrol.app.ui.theme.EchoControlTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EchoControlTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isAuthenticated = remember { mutableStateOf(false) }

                    if (isAuthenticated.value) {
                        AppNavHost()
                    } else {
                        AuthenticationScreen(onContinueAsGuest = { isAuthenticated.value = true })
                    }
                }
            }
        }
    }
}
