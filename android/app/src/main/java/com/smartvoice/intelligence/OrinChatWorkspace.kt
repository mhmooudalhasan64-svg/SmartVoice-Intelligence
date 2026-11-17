package com.smartvoice.intelligence

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun OrinChatWorkspace() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { OrinConversationStore(context) }
    var conversations by remember { mutableStateOf(store.getAll()) }
    var selectedId by remember {
        mutableStateOf(conversations.firstOrNull()?.id ?: store.create().id)
    }
    var draft by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("المحادثات تُحفظ محلياً على هذا الجهاز.") }
    val selected = conversations.firstOrNull { it.id == selectedId }

    fun refresh() {
        conversations = store.getAll()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "ORIN AI",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "مساحة المحادثات الذكية",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = {
                    val created = store.create()
                    refresh()
                    selectedId = created.id
                    draft = ""
                    notice = "بدأت محادثة جديدة."
                }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("جديدة")
                }
            }

            if (conversations.size > 1) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 112.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(conversations, key = { it.id }) { conversation ->
                        Surface(
                            onClick = {
                                selectedId = conversation.id
                                notice = "تم فتح المحادثة."
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (conversation.id == selectedId)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.background
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    conversation.title,
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1
                                )
                                IconButton(onClick = {
                                    store.delete(conversation.id)
                                    refresh()
                                    if (selectedId == conversation.id) {
                                        selectedId = conversations.firstOrNull()?.id
                                            ?: store.create().id
                                        refresh()
                                    }
                                    notice = "حُذفت المحادثة."
                                }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "حذف المحادثة",
                                        tint = Color(0xFFC56B63)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Text(
                selected?.title ?: "محادثة جديدة",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )

            if (selected?.messages.isNullOrEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            "مرحباً، أنا ORIN",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "اكتب رسالتك هنا. سيُحفظ سجل المحادثة على جهازك.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp, max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selected!!.messages, key = { it.id }) { message ->
                        val isUser = message.role == OrinMessage.ROLE_USER
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(13.dp),
                            color = if (isUser)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
                            else MaterialTheme.colorScheme.background
                        ) {
                            Column(Modifier.padding(11.dp)) {
                                Text(
                                    if (isUser) "أنت" else "ORIN",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    message.content,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.background
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        minLines = 1,
                        maxLines = 5,
                        decorationBox = { inner ->
                            if (draft.isBlank()) {
                                Text(
                                    "اكتب رسالتك...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            inner()
                        }
                    )
                    IconButton(
                        enabled = draft.isNotBlank(),
                        onClick = {
                            val text = draft.trim()
                            val targetId = selectedId
                            if (text.isNotEmpty()) {
                                store.appendMessage(
                                    targetId,
                                    OrinMessage(role = OrinMessage.ROLE_USER, content = text)
                                )
                                draft = ""
                                refresh()
                                notice = "حُفظت رسالتك. لم يُربط نموذج الذكاء الاصطناعي بعد، لذلك لم تُنشأ إجابة تلقائية."
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "حفظ الرسالة",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Text(
                notice,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                "تنبيه: واجهة المحادثة والحفظ المحلي جاهزان كمرحلة أولى؛ الاتصال بنموذج AI لم يُفعّل بعد.",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
