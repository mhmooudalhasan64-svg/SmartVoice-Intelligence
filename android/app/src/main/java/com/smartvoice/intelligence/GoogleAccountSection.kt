package com.smartvoice.intelligence

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val AccountGold = Color(0xFFD6B15C)
private val AccountMuted = Color(0xFFA6A09A)

@Composable
internal fun GoogleAccountSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val firebaseReady = remember(context) { FirebaseApp.getApps(context).isNotEmpty() }
    val auth = remember(firebaseReady) { if (firebaseReady) FirebaseAuth.getInstance() else null }
    var userEmail by remember(auth) { mutableStateOf(auth?.currentUser?.email) }
    var busy by remember { mutableStateOf(false) }
    var message by remember(firebaseReady) {
        mutableStateOf(if (firebaseReady) "سجّل الدخول لمزامنة بيانات حسابك." else "بانتظار إعداد مشروع Firebase وربط تطبيق أندرويد.")
    }

    DisposableEffect(auth) {
        if (auth == null) {
            onDispose { }
        } else {
            val listener = FirebaseAuth.AuthStateListener { userEmail = it.currentUser?.email }
            auth.addAuthStateListener(listener)
            onDispose { auth.removeAuthStateListener(listener) }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121419))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text("حساب VELORA", color = Color.White)
            Text(
                userEmail ?: message,
                color = if (userEmail != null) AccountGold else AccountMuted
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && (auth != null || !firebaseReady),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccountGold,
                    contentColor = Color(0xFF090A0D)
                ),
                onClick = {
                    if (auth == null) {
                        message = "أضف google-services.json وفعّل مزوّد Google في Firebase أولاً."
                    } else if (userEmail != null) {
                        scope.launch {
                            busy = true
                            try {
                                auth.signOut()
                                runCatching {
                                    CredentialManager.create(context).clearCredentialState(
                                        ClearCredentialStateRequest()
                                    )
                                }
                                message = "تم تسجيل الخروج من هذا الجهاز."
                            } finally {
                                busy = false
                            }
                        }
                    } else {
                        scope.launch {
                            busy = true
                            try {
                                val activity = context.findActivity()
                                    ?: error("تعذر الوصول إلى شاشة تسجيل الدخول.")
                                val clientIdResource = context.resources.getIdentifier(
                                    "default_web_client_id", "string", context.packageName
                                )
                                if (clientIdResource == 0) {
                                    error("إعداد OAuth غير موجود. نزّل google-services.json بعد تفعيل Google.")
                                }
                                val serverClientId = context.getString(clientIdResource)
                                val googleOption = GetGoogleIdOption.Builder()
                                    .setFilterByAuthorizedAccounts(false)
                                    .setServerClientId(serverClientId)
                                    .setAutoSelectEnabled(false)
                                    .build()
                                val request = GetCredentialRequest.Builder()
                                    .addCredentialOption(googleOption)
                                    .build()
                                val credential = CredentialManager.create(context)
                                    .getCredential(activity, request).credential
                                if (credential !is CustomCredential ||
                                    credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                ) {
                                    error("لم يتم استلام اعتماد Google صالح.")
                                }
                                val googleToken = GoogleIdTokenCredential.createFrom(credential.data)
                                val firebaseCredential = GoogleAuthProvider.getCredential(
                                    googleToken.idToken, null
                                )
                                auth.signInWithCredential(firebaseCredential).awaitResult()
                                message = "تم تسجيل الدخول بنجاح."
                            } catch (error: Exception) {
                                message = error.localizedMessage ?: "تعذر تسجيل الدخول. تحقق من إعداد Firebase."
                            } finally {
                                busy = false
                            }
                        }
                    }
                }
            ) {
                Text(
                    when {
                        busy -> "جارٍ المعالجة..."
                        userEmail != null -> "تسجيل الخروج"
                        else -> "المتابعة باستخدام Google"
                    }
                )
            }
            if (userEmail != null) {
                Text("يمكنك تسجيل الخروج ثم اختيار حساب Google آخر.", color = AccountMuted)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener
            if (task.isSuccessful) {
                @Suppress("UNCHECKED_CAST")
                continuation.resume(task.result as T)
            } else {
                continuation.resumeWithException(
                    task.exception ?: IllegalStateException("Firebase authentication failed.")
                )
            }
        }
    }
