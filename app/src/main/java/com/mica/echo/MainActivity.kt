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
import com.google.firebase.auth.FirebaseAuth
import com.mica.echo.auth.AuthenticationScreen
import com.mica.echo.navigation.AppNavHost
import com.mica.echo.ui.theme.EchoControlTheme
import com.mica.echo.ui.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            EchoControlTheme {
                val viewModel = remember { AppViewModel(applicationContext) }
                var loggedIn by remember {
                    mutableStateOf(FirebaseAuth.getInstance().currentUser != null)
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (loggedIn) {
                        AppNavHost(viewModel = viewModel)
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
