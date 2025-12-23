package id.xms.xcai.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM chats WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getChatsByConversation(conversationId: Long): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getChatsByConversationSync(conversationId: Long): List<ChatEntity>

    @Insert
    suspend fun insertChat(chat: ChatEntity): Long

    @Query("DELETE FROM chats WHERE conversationId = :conversationId")
    suspend fun deleteChatsInConversation(conversationId: Long)

    @Query("SELECT * FROM chats")
    suspend fun getAllChats(): List<ChatEntity>

    @Query("DELETE FROM chats")
    suspend fun deleteAllChats()

    @Delete
    suspend fun deleteChat(chat: ChatEntity)

    @Update
    suspend fun updateChat(chat: ChatEntity)
    
    @Query("DELETE FROM chats WHERE conversationId = :conversationId AND timestamp >= :timestamp")
    suspend fun deleteMessagesFromTimestamp(conversationId: Long, timestamp: Long)

}
