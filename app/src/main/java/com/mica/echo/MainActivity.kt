package com.mica.echo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mica.echo.auth.AuthenticationScreen
import com.mica.echo.navigation.AppNavHost
import com.mica.echo.ui.theme.EchoControlTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            EchoControlTheme {

                var loggedIn by remember { mutableStateOf(false) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    if (loggedIn) {
                        AppNavHost()
                    } else {
                        AuthenticationScreen(
                            onLoginSuccess = {
                                loggedIn = true
                            }
                        )
                    }

                }
            }
        }
    }
}
