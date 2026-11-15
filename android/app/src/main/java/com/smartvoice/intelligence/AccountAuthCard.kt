package com.aurevon.velora

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
    var status by remember { mutableStateOf("أنشئ حسابك أو سجّل الدخول لمزامنة بياناتك.") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isCreateAccount by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    val googleConfigured = GOOGLE_WEB_CLIENT_ID != "CONFIGURE_WEB_CLIENT_ID_IN_FIREBASE"

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { signedInUser = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    fun runAuth(action: suspend () -> Unit) {
        scope.launch {
            loading = true
            status = "جارٍ تنفيذ الطلب..."
            try {
                action()
                status = "تمت العملية بنجاح."
            } catch (error: Exception) {
                status = when {
                    error.message?.contains("email address is badly", true) == true -> "صيغة البريد الإلكتروني غير صحيحة."
                    error.message?.contains("password is invalid", true) == true -> "كلمة المرور غير صحيحة."
                    error.message?.contains("no user record", true) == true -> "لا يوجد حساب بهذا البريد الإلكتروني."
                    error.message?.contains("already in use", true) == true -> "هذا البريد مسجل مسبقاً."
                    error.message?.contains("at least 6", true) == true -> "كلمة المرور يجب أن تتكون من 6 أحرف على الأقل."
                    else -> "تعذر إكمال العملية: ${error.localizedMessage ?: "تحقق من الاتصال والإعدادات"}"
                }
            } finally {
                loading = false
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("حساب VELORA", color = Color.White, fontWeight = FontWeight.Bold)
            if (signedInUser == null) {
                Text("اختر طريقة الدخول المناسبة لك.", color = Color(0xFF9CA3AF))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("البريد الإلكتروني") },
                    singleLine = true,
                    enabled = !loading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("كلمة المرور") },
                    singleLine = true,
                    enabled = !loading,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(onClick = { showPassword = !showPassword }) {
                            Text(if (showPassword) "إخفاء" else "إظهار")
                        }
                    }
                )
                if (isCreateAccount) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("تأكيد كلمة المرور") },
                        singleLine = true,
                        enabled = !loading,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                }

                Button(
                    onClick = {
                        when {
                            email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                                status = "أدخل بريداً إلكترونياً صحيحاً."
                            password.length < 6 -> status = "كلمة المرور يجب أن تتكون من 6 أحرف على الأقل."
                            isCreateAccount && password != confirmPassword -> status = "كلمتا المرور غير متطابقتين."
                            else -> runAuth {
                                if (isCreateAccount) auth.createUserWithEmailAndPassword(email, password).await()
                                else auth.signInWithEmailAndPassword(email, password).await()
                            }
                        }
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD5AE63), contentColor = Color(0xFF171717))
                ) {
                    Text(if (loading) "يرجى الانتظار..." else if (isCreateAccount) "إنشاء حساب بالبريد الإلكتروني" else "تسجيل الدخول بالبريد الإلكتروني")
                }

                TextButton(
                    onClick = {
                        isCreateAccount = !isCreateAccount
                        status = if (isCreateAccount) "يمكنك الآن تسجيل الدخول." else "أدخل بياناتك لإنشاء حساب جديد."
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isCreateAccount) "لديك حساب بالفعل؟ تسجيل الدخول" else "ليس لديك حساب؟ إنشاء حساب جديد")
                }
                if (!isCreateAccount) {
                    TextButton(
                        onClick = {
                            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                                status = "أدخل بريدك الإلكتروني الصحيح أولاً."
                            } else runAuth { auth.sendPasswordResetEmail(email).await() }
                        },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("نسيت كلمة المرور؟ إرسال رابط الاستعادة") }
                }

                Text("أو", color = Color(0xFF9CA3AF))
                Button(
                    onClick = {
                        runAuth {
                            val googleOption = GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setServerClientId(GOOGLE_WEB_CLIENT_ID)
                                .setAutoSelectEnabled(false)
                                .build()
                            val request = GetCredentialRequest.Builder().addCredentialOption(googleOption).build()
                            val result = credentialManager.getCredential(context, request)
                            val credential = result.credential
                            if (credential !is CustomCredential ||
                                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                            ) error("لم يتم إرجاع بيانات اعتماد Google صالحة")
                            val token = GoogleIdTokenCredential.createFrom(credential.data)
                            auth.signInWithCredential(GoogleAuthProvider.getCredential(token.idToken, null)).await()
                        }
                    },
                    enabled = googleConfigured && !loading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF374151))
                ) { Text("المتابعة باستخدام Google") }
                if (!googleConfigured) {
                    Text("تسجيل Google ينتظر إكمال إعداد OAuth في Firebase Console.", color = Color(0xFFFFD28A))
                }
            } else {
                Text("تم تسجيل الدخول: ${signedInUser?.email ?: "حسابك"}", color = Color.White)
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
                ) { Text("تسجيل الخروج") }
            }
            Text(status, color = if (status.startsWith("تعذر")) Color(0xFFFFA6A6) else Color(0xFF9CA3AF))
        }
    }
}
