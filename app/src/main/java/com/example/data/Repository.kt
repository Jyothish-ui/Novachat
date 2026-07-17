package com.example.data

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID

class ChatRepository(private val context: Context, private val externalScope: CoroutineScope) {

    private val db = AppDatabase.getDatabase(context)

    val chatDao = db.chatDao()

    val allChats: Flow<List<ChatEntity>> = chatDao.getAllChats()
    val allUsers: Flow<List<UserEntity>> = chatDao.getAllUsers()

    fun getMessagesForChat(chatId: String): Flow<List<MessageEntity>> {
        return chatDao.getMessagesForChat(chatId)
    }

    suspend fun getChatById(chatId: String): ChatEntity? {
        return chatDao.getChatById(chatId)
    }

    suspend fun getUserById(userId: String): UserEntity? {
        return chatDao.getUserById(userId)
    }

    suspend fun markChatAsRead(chatId: String, myId: String) {
        chatDao.markChatAsRead(chatId)
        chatDao.markMessagesAsRead(chatId, myId)
    }

    // Initialize Mock Contacts, Group, and Gemini Bot in Database
    suspend fun initializeDatabase() {
        // Only run if database is empty
        val existingChats = chatDao.getAllChats()
        // Wait, since we are returning Flow, we can run a check
        val existingUsers = chatDao.getUserById("user_alice")
        if (existingUsers == null) {
            val systemUsers = listOf(
                UserEntity("user_me", "You", "avatar_me", isOnline = true, isMe = true),
                UserEntity("user_gemini", "Gemini AI", "avatar_gemini", isOnline = true, isAi = true),
                UserEntity("user_alice", "Alice (UI/UX Designer)", "avatar_alice", isOnline = true),
                UserEntity("user_bob", "Bob (Compose Engineer)", "avatar_bob", isOnline = false),
                UserEntity("user_charlie", "Charlie (Product Manager)", "avatar_charlie", isOnline = true)
            )
            chatDao.insertUsers(systemUsers)

            // Direct Chats
            val initialChats = listOf(
                ChatEntity("chat_gemini", "Gemini AI Assistant", isGroup = false, unreadCount = 0),
                ChatEntity("chat_alice", "Alice (UI/UX Designer)", isGroup = false, unreadCount = 1),
                ChatEntity("chat_bob", "Bob (Compose Engineer)", isGroup = false, unreadCount = 0),
                ChatEntity("chat_charlie", "Charlie (Product Manager)", isGroup = false, unreadCount = 0),
                ChatEntity("chat_group_dev", "NovaChat Dev Team", isGroup = true, groupMembersJson = "user_me,user_alice,user_bob,user_charlie")
            )
            for (chat in initialChats) {
                chatDao.insertChat(chat)
            }

            // Seed messages
            chatDao.insertMessage(MessageEntity("msg_1", "chat_alice", "user_alice", "Alice (UI/UX Designer)", "Hey! I just updated the design system. Let me know what you think of the new neon dark theme! 🎨✨", System.currentTimeMillis() - 1000 * 60 * 5, "TEXT", status = "SENT"))
            chatDao.updateLastMessage("chat_alice", "Hey! I just updated the design system. Let me know what you think of the new neon dark theme! 🎨✨", System.currentTimeMillis() - 1000 * 60 * 5)

            chatDao.insertMessage(MessageEntity("msg_2", "chat_bob", "user_bob", "Bob (Compose Engineer)", "The edge-to-edge layout is working beautifully now with WindowInsets! Check out MainActivity.", System.currentTimeMillis() - 1000 * 60 * 60 * 2, "TEXT", status = "READ"))
            chatDao.updateLastMessage("chat_bob", "The edge-to-edge layout is working beautifully now with WindowInsets! Check out MainActivity.", System.currentTimeMillis() - 1000 * 60 * 60 * 2)

            chatDao.insertMessage(MessageEntity("msg_3", "chat_gemini", "user_gemini", "Gemini AI Assistant", "Hi! I am your NovaChat AI Assistant. You can send me text, questions, or pictures, and I'll help you build something great. 🚀🤖", System.currentTimeMillis() - 1000 * 60 * 60 * 5, "TEXT", status = "READ"))
            chatDao.updateLastMessage("chat_gemini", "Hi! I am your NovaChat AI Assistant. You can send me text, questions, or pictures, and I'll help you build something great. 🚀🤖", System.currentTimeMillis() - 1000 * 60 * 60 * 5)

            chatDao.insertMessage(MessageEntity("msg_4", "chat_group_dev", "user_charlie", "Charlie (Product Manager)", "Sprint review is at 4 PM today. Let's make sure our media attachments and calling modules are ready!", System.currentTimeMillis() - 1000 * 60 * 10, "TEXT", status = "READ"))
            chatDao.updateLastMessage("chat_group_dev", "Charlie: Sprint review is at 4 PM today. Let's make sure our media attachments and calling modules are ready!", System.currentTimeMillis() - 1000 * 60 * 10)
        }
    }

    suspend fun sendMessage(
        chatId: String,
        senderId: String,
        senderName: String,
        text: String,
        type: String = "TEXT",
        mediaPath: String? = null,
        mediaDurationMs: Long? = null
    ) {
        val msgId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val message = MessageEntity(
            id = msgId,
            chatId = chatId,
            senderId = senderId,
            senderName = senderName,
            text = text,
            timestamp = timestamp,
            type = type,
            mediaPath = mediaPath,
            mediaDurationMs = mediaDurationMs,
            status = if (senderId == "user_me") "SENDING" else "SENT"
        )
        chatDao.insertMessage(message)

        // Update chat's last message
        val prefix = if (senderId == "user_me") "You: " else "$senderName: "
        val displayMsg = when (type) {
            "IMAGE" -> "📸 Image Shared"
            "VOICE" -> "🎤 Voice Message (${(mediaDurationMs ?: 0L) / 1000}s)"
            else -> text
        }
        chatDao.updateLastMessage(chatId, if (chatId.contains("group")) "$senderName: $displayMsg" else displayMsg, timestamp)

        if (senderId == "user_me") {
            // Update status to SENT
            delay(300)
            chatDao.insertMessage(message.copy(status = "SENT"))

            // Trigger AI response or simulated contact reply
            externalScope.launch(Dispatchers.IO) {
                handleAutomaticReply(chatId, text, type, mediaPath)
            }
        }
    }

    private suspend fun handleAutomaticReply(chatId: String, userText: String, type: String, mediaPath: String?) {
        delay(1000) // Delay before typing starts
        val chat = getChatById(chatId) ?: return

        if (chat.isGroup) {
            // Pick a group member to reply (either Alice, Bob, or Charlie)
            val potentialSenders = listOf(
                UserEntity("user_alice", "Alice (UI/UX Designer)", "avatar_alice", isOnline = true),
                UserEntity("user_bob", "Bob (Compose Engineer)", "avatar_bob", isOnline = true),
                UserEntity("user_charlie", "Charlie (Product Manager)", "avatar_charlie", isOnline = true)
            )
            val sender = potentialSenders.random()

            // Update user to typing
            chatDao.updateUserTyping(sender.id, chatId)
            delay(2500) // typing duration
            chatDao.updateUserTyping(sender.id, null)

            val replyText = getMockGroupReply(sender.id, userText)
            sendMessage(chatId, sender.id, sender.name, replyText)
            chatDao.incrementUnreadCount(chatId)
        } else {
            // Direct chat
            val contactId = when (chatId) {
                "chat_gemini" -> "user_gemini"
                "chat_alice" -> "user_alice"
                "chat_bob" -> "user_bob"
                "chat_charlie" -> "user_charlie"
                else -> return
            }
            val contact = getUserById(contactId) ?: return

            // Typing feedback
            chatDao.updateUserTyping(contactId, chatId)

            // Calculate reply
            val replyText = if (contactId == "user_gemini") {
                // Real Gemini API response!
                var base64Img: String? = null
                var mimeType: String? = null
                if (type == "IMAGE" && mediaPath != null) {
                    try {
                        val file = java.io.File(mediaPath)
                        if (file.exists()) {
                            val bytes = file.readBytes()
                            base64Img = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                            mimeType = "image/jpeg"
                        }
                    } catch (e: Exception) {
                        Log.e("ChatRepository", "Failed to load base64 image", e)
                    }
                }
                GeminiRetrofitClient.fetchGeminiResponse(userText, base64Img, mimeType)
            } else {
                // Let's use Gemini to generate a response from Alice/Bob/Charlie if we have the API key!
                val systemPrompt = when (contactId) {
                    "user_alice" -> "You are Alice, a passionate Product Designer. Keep your response brief, friendly, under 2 sentences, and use lots of creative emojis. The user sent: $userText"
                    "user_bob" -> "You are Bob, an expert Android Developer who loves Kotlin, Compose, and clean architecture. Keep your response tech-focused, brief, under 2 sentences, and slightly geeky. The user sent: $userText"
                    else -> "You are Charlie, a senior Product Manager. Keep your response focused on sprints, deliverables, and team alignment. Be concise, professional, under 2 sentences. The user sent: $userText"
                }
                
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY") {
                    GeminiRetrofitClient.fetchGeminiResponse(systemPrompt)
                } else {
                    // Fallback mock responses
                    getMockContactReply(contactId, userText)
                }
            }

            delay(2000) // standard reading/typing time
            chatDao.updateUserTyping(contactId, null)

            sendMessage(chatId, contactId, contact.name, replyText)
            chatDao.incrementUnreadCount(chatId)
        }
    }

    private fun getMockContactReply(contactId: String, message: String): String {
        val msg = message.lowercase()
        return when (contactId) {
            "user_alice" -> {
                when {
                    msg.contains("color") || msg.contains("theme") || msg.contains("design") ->
                        "I used deep teal & slate to make sure the contrast meets AAA compliance! Let me know if the glowing accent pops! 🎨✨"
                    msg.contains("call") || msg.contains("video") || msg.contains("phone") ->
                        "Ooh, let's test out the video call! The CameraX resolution looks super smooth. 🎥💖"
                    else -> listOf(
                        "That looks amazing! Let me add it to our design Figma file. 📐💖",
                        "Got it! Let's schedule a design critique tomorrow morning. 🎨☕",
                        "Love this idea! Can we try a version with slightly more negative space? 🌌",
                        "Hey, do you think we should use rounded corners on these cards? I'm leaning toward 16.dp!"
                    ).random()
                }
            }
            "user_bob" -> {
                when {
                    msg.contains("build") || msg.contains("error") || msg.contains("gradle") ->
                        "Ah, try cleaning the gradle cache or checking if KSP matches Kotlin version 2.2.10! Let me know if that works. 🛠️"
                    msg.contains("database") || msg.contains("room") ->
                        "Nice! Room persistence is so reactive with Flow. I set exportSchema = false for this. 🗄️⚡"
                    else -> listOf(
                        "I'm working on refactoring the view models with StateFlow. Clean architecture makes everything super decoupled! 🚀",
                        "Just tested the voice message module. Audio is recording smoothly at 16kHz! 🎤",
                        "Let me push this branch and merge it into main. Build is fully green! ✅",
                        "Coroutines make managing this simulated typing delay incredibly simple!"
                    ).random()
                }
            }
            else -> { // Charlie
                listOf(
                    "Excellent progress. Let's make sure this is highlighted in the sprint demo! 📈",
                    "We need to stay aligned with the client scope. Is this item part of the MVP? 🎯",
                    "Great work! Let's check this ticket off in JIRA and move on to calling testing. 📅",
                    "Thanks for the update. Let's touch base during the daily standup."
                ).random()
            }
        }
    }

    private fun getMockGroupReply(contactId: String, message: String): String {
        return when (contactId) {
            "user_alice" -> "Oh! I'm already drafting a mockup for that. 🎨"
            "user_bob" -> "I can implement that custom Compose Canvas tonight. 💻"
            else -> "Perfect team. Let's prioritize finishing this for our release today! 🚀"
        }
    }
}
