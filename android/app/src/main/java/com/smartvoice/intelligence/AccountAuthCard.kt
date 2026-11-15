package com.aurevon.velora

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val GOOGLE_WEB_CLIENT_ID = "CONFIGURE_WEB_CLIENT_ID_IN_FIREBASE"

@Composable
fun AccountAuthCard() {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val credentialManager = remember { CredentialManager.create(context) }
    val scope = rememberCoroutineScope()
    var signedInUser by remember { mutableStateOf(auth.currentUser) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("سجّل الدخول لمزامنة حسابك بأمان.") }
    val configured = GOOGLE_WEB_CLIENT_ID != "CONFIGURE_WEB_CLIENT_ID_IN_FIREBASE"

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            signedInUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("حساب VELORA", color = Color.White, fontWeight = FontWeight.Bold)
            if (signedInUser == null) {
                Text(
                    if (configured) status else "تم ربط Firebase. بقي تفعيل Google وإضافة OAuth Web Client ID من Firebase Console.",
                    color = Color(0xFF9CA3AF)
                )
                Button(
                    onClick = {
                        scope.launch {
                            loading = true
                            status = "جارٍ الاتصال بحساب Google..."
                            try {
                                val googleOption = GetGoogleIdOption.Builder()
                                    .setFilterByAuthorizedAccounts(false)
                                    .setServerClientId(GOOGLE_WEB_CLIENT_ID)
                                    .setAutoSelectEnabled(false)
                                    .build()
                                val request = GetCredentialRequest.Builder()
                                    .addCredentialOption(googleOption)
                                    .build()
                                val result = credentialManager.getCredential(context, request)
                                val credential = result.credential
                                if (credential !is CustomCredential ||
                                    credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                ) {
                                    error("لم يتم إرجاع بيانات اعتماد Google صالحة")
                                }
                                val googleToken = GoogleIdTokenCredential.createFrom(credential.data)
                                val firebaseCredential = GoogleAuthProvider.getCredential(googleToken.idToken, null)
                                auth.signInWithCredential(firebaseCredential).await()
                                status = "تم تسجيل الدخول بنجاح."
                            } catch (error: GetCredentialException) {
                                status = "تعذر تسجيل الدخول: ${error.message ?: "أعد المحاولة"}"
                            } catch (error: Exception) {
                                status = "تعذر تسجيل الدخول: ${error.message ?: "حدث خطأ غير متوقع"}"
                            } finally {
                                loading = false
                            }
                        }
                    },
                    enabled = configured && !loading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C9EFF))
                ) {
                    Text(if (loading) "جارٍ تسجيل الدخول..." else "المتابعة باستخدام Google")
                }
                if (!configured) {
                    Text("زر الدخول سيُفعّل بعد إضافة معرّف عميل الويب OAuth إلى إعدادات التطبيق.", color = Color(0xFFFFD28A))
                }
            } else {
                Text("تم تسجيل الدخول: ${signedInUser?.email ?: "حساب Google"}", color = Color.White)
                Button(
                    onClick = {
                        auth.signOut()
                        scope.launch {
                            try { credentialManager.clearCredentialState(androidx.credentials.ClearCredentialStateRequest()) }
                            catch (_: Exception) { }
                        }
                        status = "تم تسجيل الخروج."
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("تسجيل الخروج")
                }
            }
        }
    }
}
