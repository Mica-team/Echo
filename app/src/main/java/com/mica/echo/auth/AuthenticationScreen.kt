package com.mica.echo.auth

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun AuthenticationScreen(
    onLoginSuccess: () -> Unit
) {

    val context = LocalContext.current
    val activity = context as Activity

    val manager = remember {
        GoogleSignInManager(activity)
    }

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->

            manager.handleResult(
                result.data?.data?.let { Intent().setData(it) } ?: result.data,
                onSuccess = {
    onLoginSuccess()
                },
                onError = {
                    println(it)
                }
            )
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            "Echo Control",
            style = MaterialTheme.typography.headlineLarge
        )

        Text(
            "So you here to connect your robots, etc, then login",
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors()
        ) {

            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        manager.signIn(launcher)
                    }
                ) {
                    Text("Continue with Google")
                }

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        // Microsoft login later
                    }
                ) {
                    Text("Continue with Microsoft")
                }

            }

        }

    }
}
