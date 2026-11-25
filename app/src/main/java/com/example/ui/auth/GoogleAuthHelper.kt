package com.example.ui.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val AUTH_TAG = "FoxAuth"

sealed interface AuthGateState {
  data object Loading : AuthGateState
  data object SignedOut : AuthGateState
  data class SignedIn(val user: FirebaseUser) : AuthGateState
}

fun FirebaseAuth.authStateFlow(): Flow<AuthGateState> = callbackFlow {
  val listener = FirebaseAuth.AuthStateListener { auth ->
    val current = auth.currentUser
    trySend(if (current != null) AuthGateState.SignedIn(current) else AuthGateState.SignedOut)
  }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

/**
 * Silent session restore on startup for previously authorized Google accounts.
 * Uses strictly ONE credential option per GetCredentialRequest for Android 16 (API 36) compatibility.
 */
suspend fun attemptSilentGoogleSignIn(context: Context): Result<Unit> {
  if (Firebase.auth.currentUser != null) return Result.success(Unit)
  val webClientId = context.getString(R.string.default_web_client_id)
  if (webClientId.isBlank()) return Result.failure(IllegalStateException("Thiếu default_web_client_id"))

  val credentialManager = CredentialManager.create(context)
  val googleIdOption = GetGoogleIdOption.Builder()
    .setFilterByAuthorizedAccounts(true)
    .setServerClientId(webClientId)
    .setAutoSelectEnabled(true)
    .build()
  val request = GetCredentialRequest.Builder()
    .addCredentialOption(googleIdOption)
    .build()

  return try {
    val response = credentialManager.getCredential(context, request)
    val credential = response.credential
    if (credential is CustomCredential &&
      credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
      val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
      Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
      Result.success(Unit)
    } else {
      Result.failure(IllegalStateException("Thông tin xác thực Google không hợp lệ."))
    }
  } catch (e: CancellationException) {
    throw e
  } catch (e: NoCredentialException) {
    Log.i(AUTH_TAG, "No previously authorized Google credential for silent sign-in.")
    Result.failure(e)
  } catch (e: GetCredentialException) {
    Log.w(AUTH_TAG, "Silent Google sign-in skipped: ${e.message}")
    Result.failure(e)
  } catch (e: Exception) {
    Log.w(AUTH_TAG, "Silent Google sign-in error", e)
    Result.failure(e)
  }
}

/**
 * Interactive Google Sign-In button flow using GetSignInWithGoogleOption (1 option per request).
 */
suspend fun performInteractiveGoogleSignIn(context: Context): Result<FirebaseUser> {
  val webClientId = context.getString(R.string.default_web_client_id)
  if (webClientId.isBlank()) {
    return Result.failure(
      IllegalStateException("Chưa cấu hình default_web_client_id trong google-services.json.")
    )
  }

  val credentialManager = CredentialManager.create(context)
  val signInOption = GetSignInWithGoogleOption.Builder(webClientId).build()
  val request = GetCredentialRequest.Builder()
    .addCredentialOption(signInOption)
    .build()

  return try {
    val response = credentialManager.getCredential(context, request)
    val credential = response.credential
    if (credential is CustomCredential &&
      credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
      val googleToken = GoogleIdTokenCredential.createFrom(credential.data)
      val authResult = Firebase.auth
        .signInWithCredential(GoogleAuthProvider.getCredential(googleToken.idToken, null))
        .await()
      val user = authResult.user
        ?: return Result.failure(IllegalStateException("Đăng nhập thành công nhưng không nhận được hồ sơ người dùng."))
      Result.success(user)
    } else {
      Result.failure(IllegalStateException("Phản hồi Google Credential không đúng định dạng."))
    }
  } catch (e: CancellationException) {
    throw e
  } catch (e: GetCredentialCancellationException) {
    Log.w(AUTH_TAG, "User cancelled Google Sign-In picker.")
    Result.failure(e)
  } catch (e: NoCredentialException) {
    Log.w(AUTH_TAG, "No Google account available on device: ${e.message}")
    Result.failure(
      IllegalStateException(
        "Thiết bị/máy ảo chưa đăng nhập tài khoản Google. Hãy thêm tài khoản Google trong Cài đặt Android hoặc dùng Chế độ DEMO cục bộ."
      )
    )
  } catch (e: GetCredentialException) {
    Log.e(AUTH_TAG, "Google Sign-In failed: ${e.type} - ${e.message}", e)
    Result.failure(
      IllegalStateException("Đăng nhập Google thất bại (${e.errorMessage ?: e.message ?: "lỗi xác thực"}). Vui lòng thử lại.")
    )
  } catch (e: Exception) {
    Log.e(AUTH_TAG, "Firebase Google sign-in error", e)
    Result.failure(e)
  }
}

/**
 * Safe sign-out wrapped in NonCancellable so Credential Manager cleanup completes
 * cleanly even if the composable leaves composition immediately.
 */
suspend fun signOutGoogleSafely(context: Context) {
  withContext(NonCancellable) {
    try {
      CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    } catch (e: Exception) {
      Log.w(AUTH_TAG, "clearCredentialState warning: ${e.message}")
    }
    Firebase.auth.signOut()
  }
}
