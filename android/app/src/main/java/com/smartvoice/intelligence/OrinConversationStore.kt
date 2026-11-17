package com.smartvoice.intelligence

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Local conversation history for ORIN.
 *
 * This store is intentionally independent from authentication and remote services.
 * Messages remain on this device until the user clears the app data or deletes them.
 */
data class OrinMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(role == ROLE_USER || role == ROLE_ASSISTANT || role == ROLE_SYSTEM) {
            "Unsupported ORIN message role"
        }
    }

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_SYSTEM = "system"
    }
}

data class OrinConversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "محادثة جديدة",
    val messages: List<OrinMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
)

/**
 * A small, versioned local persistence layer. It does not require an account,
 * Firebase, a network connection, or an API key.
 */
class OrinConversationStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun getAll(): List<OrinConversation> = decode(preferences.getString(KEY_CONVERSATIONS, null))
        .sortedByDescending { it.updatedAt }

    @Synchronized
    fun getById(conversationId: String): OrinConversation? =
        getAll().firstOrNull { it.id == conversationId }

    @Synchronized
    fun create(title: String = "محادثة جديدة"): OrinConversation {
        val now = System.currentTimeMillis()
        val conversation = OrinConversation(
            title = title.trim().ifBlank { "محادثة جديدة" },
            createdAt = now,
            updatedAt = now
        )
        saveAll(getAll() + conversation)
        return conversation
    }

    @Synchronized
    fun appendMessage(conversationId: String, message: OrinMessage): OrinConversation? {
        val conversations = getAll()
        val index = conversations.indexOfFirst { it.id == conversationId }
        if (index < 0) return null

        val current = conversations[index]
        val updatedMessages = current.messages + message
        val firstUserMessage = updatedMessages.firstOrNull { it.role == OrinMessage.ROLE_USER }
        val updated = current.copy(
            title = if (current.title == "محادثة جديدة" && firstUserMessage != null) {
                firstUserMessage.content.replace(Regex("\\s+"), " ").take(42)
                    .ifBlank { current.title }
            } else current.title,
            messages = updatedMessages,
            updatedAt = System.currentTimeMillis()
        )
        saveAll(conversations.toMutableList().also { it[index] = updated })
        return updated
    }

    @Synchronized
    fun delete(conversationId: String) {
        saveAll(getAll().filterNot { it.id == conversationId })
    }

    @Synchronized
    fun clear() {
        preferences.edit().remove(KEY_CONVERSATIONS).apply()
    }

    private fun saveAll(conversations: List<OrinConversation>) {
        val array = JSONArray()
        conversations.forEach { conversation ->
            val messages = JSONArray()
            conversation.messages.forEach { message ->
                messages.put(JSONObject().apply {
                    put("id", message.id)
                    put("role", message.role)
                    put("content", message.content)
                    put("createdAt", message.createdAt)
                })
            }
            array.put(JSONObject().apply {
                put("id", conversation.id)
                put("title", conversation.title)
                put("createdAt", conversation.createdAt)
                put("updatedAt", conversation.updatedAt)
                put("messages", messages)
            })
        }
        preferences.edit().putString(KEY_CONVERSATIONS, array.toString()).apply()
    }

    private fun decode(raw: String?): List<OrinConversation> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val messageArray = item.optJSONArray("messages") ?: JSONArray()
                    val messages = buildList {
                        for (j in 0 until messageArray.length()) {
                            val message = messageArray.optJSONObject(j) ?: continue
                            val role = message.optString("role")
                            if (role !in setOf(
                                    OrinMessage.ROLE_USER,
                                    OrinMessage.ROLE_ASSISTANT,
                                    OrinMessage.ROLE_SYSTEM
                                )
                            ) continue
                            add(
                                OrinMessage(
                                    id = message.optString("id").ifBlank { UUID.randomUUID().toString() },
                                    role = role,
                                    content = message.optString("content"),
                                    createdAt = message.optLong("createdAt")
                                )
                            )
                        }
                    }
                    val createdAt = item.optLong("createdAt", System.currentTimeMillis())
                    add(
                        OrinConversation(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            title = item.optString("title").ifBlank { "محادثة جديدة" },
                            messages = messages,
                            createdAt = createdAt,
                            updatedAt = item.optLong("updatedAt", createdAt)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "orin_conversation_history"
        private const val KEY_CONVERSATIONS = "conversations_v1"
    }
}
