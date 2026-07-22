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
    context: Context
) {

    private val auth = FirebaseAuth.getInstance()

    private val googleClient = GoogleSignIn.getClient(
        context,
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
    )

    fun signIn(
        launcher: ActivityResultLauncher<Intent>
    ) {
        launcher.launch(googleClient.signInIntent)
    }

    fun handleResult(
        data: Intent?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {

        try {

            val account =
                GoogleSignIn.getSignedInAccountFromIntent(data)
                    .getResult(ApiException::class.java)

            val credential =
                GoogleAuthProvider.getCredential(account.idToken, null)

            auth.signInWithCredential(credential)
                .addOnSuccessListener {
                    onSuccess()
                }
                .addOnFailureListener {
                    onError(it.localizedMessage ?: "Authentication Failed")
                }

        } catch (e: Exception) {
            onError(e.localizedMessage ?: "Google Sign-In Failed")
        }

    }

}
