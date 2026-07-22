package com.mica.echo.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.mica.echo.R

class GoogleSignInManager(
    private val context: Context,
    private val launcher: ActivityResultLauncher<Intent>,
    private val onSuccess: () -> Unit,
    private val onFailure: (String) -> Unit
) {

    private val auth = FirebaseAuth.getInstance()

    private val gso = GoogleSignInOptions.Builder(
        GoogleSignInOptions.DEFAULT_SIGN_IN
    )
        .requestIdToken(context.getString(R.string.default_web_client_id))
        .requestEmail()
        .build()

    private val client = GoogleSignIn.getClient(context, gso)

    fun signIn() {
        launcher.launch(client.signInIntent)
    }

    fun handleResult(data: Intent?) {
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)

            val credential =
                GoogleAuthProvider.getCredential(account.idToken, null)

            auth.signInWithCredential(credential)
                .addOnSuccessListener {
                    onSuccess()
                }
                .addOnFailureListener {
                    onFailure(it.message ?: "Authentication failed")
                }

        } catch (e: Exception) {
            onFailure(e.message ?: "Google Sign-In failed")
        }
    }
}
