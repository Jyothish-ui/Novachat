package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatarUrl: String,
    val isOnline: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis(),
    val isMe: Boolean = false,
    val isAi: Boolean = false,
    val typingChatId: String? = null // chatId if they are currently typing in it
)

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isGroup: Boolean = false,
    val groupMembersJson: String = "", // Comma-separated user IDs
    val unreadCount: Int = 0,
    val lastMessageText: String = "",
    val lastMessageTime: Long = 0L
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "TEXT", // "TEXT", "IMAGE", "VOICE"
    val mediaPath: String? = null, // Local URI or file path
    val mediaDurationMs: Long? = null, // For voice messages
    val status: String = "SENT" // "SENDING", "SENT", "READ"
)

@Dao
interface ChatDao {
    // Users Queries
    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Query("UPDATE users SET isOnline = :isOnline, lastSeen = :lastSeen WHERE id = :userId")
    suspend fun updateUserStatus(userId: String, isOnline: Boolean, lastSeen: Long)

    @Query("UPDATE users SET typingChatId = :chatId WHERE id = :userId")
    suspend fun updateUserTyping(userId: String, chatId: String?)

    // Chats Queries
    @Query("SELECT * FROM chats ORDER BY lastMessageTime DESC")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :chatId LIMIT 1")
    suspend fun getChatById(chatId: String): ChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)

    @Query("UPDATE chats SET unreadCount = 0 WHERE id = :chatId")
    suspend fun markChatAsRead(chatId: String)

    @Query("UPDATE chats SET lastMessageText = :text, lastMessageTime = :time WHERE id = :chatId")
    suspend fun updateLastMessage(chatId: String, text: String, time: Long)

    @Query("UPDATE chats SET unreadCount = unreadCount + 1 WHERE id = :chatId")
    suspend fun incrementUnreadCount(chatId: String)

    // Messages Queries
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChat(chatId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("UPDATE messages SET status = :status WHERE chatId = :chatId AND senderId != :myId")
    suspend fun markMessagesAsRead(chatId: String, myId: String, status: String = "READ")
}

@Database(entities = [UserEntity::class, ChatEntity::class, MessageEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "novachat_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
