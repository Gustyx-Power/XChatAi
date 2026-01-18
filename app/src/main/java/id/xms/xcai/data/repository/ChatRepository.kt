package id.xms.xcai.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import id.xms.xcai.data.local.ChatDatabase
import id.xms.xcai.data.local.ChatEntity
import id.xms.xcai.data.local.ConversationEntity
import id.xms.xcai.data.remote.GroqApiService
import id.xms.xcai.data.remote.GroqChatRequest
import id.xms.xcai.data.remote.GroqVisionRequest
import id.xms.xcai.data.remote.VisionMessage
import id.xms.xcai.data.remote.ContentPart
import id.xms.xcai.data.remote.ImageUrl
import id.xms.xcai.data.remote.Message
import id.xms.xcai.data.preferences.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class RateLimitData(
    @get:com.google.firebase.database.PropertyName("count")
    @set:com.google.firebase.database.PropertyName("count")
    var count: Int = 0,

    @get:com.google.firebase.database.PropertyName("windowStart")
    @set:com.google.firebase.database.PropertyName("windowStart")
    var windowStart: Long = 0L,

    @get:com.google.firebase.database.PropertyName("lastRequest")
    @set:com.google.firebase.database.PropertyName("lastRequest")
    var lastRequest: Long = 0L
) {
    constructor() : this(0, 0L, 0L)
}

class ChatRepository(context: Context) {

    private val database = ChatDatabase.getDatabase(context)
    private val chatDao = database.chatDao()
    private val conversationDao = database.conversationDao()
    private val groqApi = GroqApiService.create()
    private val firebaseDb = FirebaseDatabase.getInstance()
    private val preferencesManager = UserPreferences(context)

    private val MAX_HISTORY_MESSAGES = 100

    companion object {
        private const val TIME_WINDOW_MS = 20 * 60 * 1000L // 20 menit
    }

    fun getConversationsByUser(userId: String): Flow<List<ConversationEntity>> {
        return conversationDao.getConversationsByUser(userId)
    }

    suspend fun createConversation(userId: String, title: String): Long {
        val conversation = ConversationEntity(
            title = title,
            userId = userId
        )
        return conversationDao.insertConversation(conversation)
    }

    /**
     * Delete a specific chat message
     */
    suspend fun deleteChat(chat: ChatEntity) = withContext(Dispatchers.IO) {
        database.chatDao().deleteChat(chat)
    }

    /**
     * Update a chat message
     */
    suspend fun updateChat(chat: ChatEntity) = withContext(Dispatchers.IO) {
        database.chatDao().updateChat(chat)
    }


    suspend fun updateConversation(conversation: ConversationEntity) {
        conversationDao.updateConversation(conversation)
    }

    suspend fun deleteConversation(conversation: ConversationEntity) {
        conversationDao.deleteConversation(conversation)
    }

    suspend fun getConversationById(id: Long): ConversationEntity? {
        return conversationDao.getConversationById(id)
    }

    fun getChatsByConversation(conversationId: Long): Flow<List<ChatEntity>> {
        return chatDao.getChatsByConversation(conversationId)
    }

    suspend fun insertChat(chat: ChatEntity): Long {
        return chatDao.insertChat(chat)
    }

    suspend fun deleteChatsInConversation(conversationId: Long) {
        chatDao.deleteChatsInConversation(conversationId)
    }
    
    suspend fun deleteMessagesFromTimestamp(conversationId: Long, timestamp: Long) {
        chatDao.deleteMessagesFromTimestamp(conversationId, timestamp)
    }

    // IMPROVED: Better error handling with try-catch
    suspend fun checkServerRateLimit(userId: String, maxRequests: Int = 25): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            try {
                // If unlimited, always allow
                if (maxRequests == Int.MAX_VALUE) {
                    Log.d("ChatRepository", "Unlimited requests - allowed")
                    return@withContext Pair(true, "")
                }

                suspendCoroutine { continuation ->
                    val rateLimitRef = firebaseDb.getReference("rate_limits/$userId")

                    try {
                        rateLimitRef.runTransaction(object : Transaction.Handler {
                            override fun doTransaction(currentData: MutableData): Transaction.Result {
                                return try {
                                    val currentTime = System.currentTimeMillis()

                                    val data = currentData.getValue(RateLimitData::class.java)
                                        ?: RateLimitData()

                                    Log.d("ChatRepository", "Transaction - Current time: $currentTime")
                                    Log.d("ChatRepository", "Transaction - Window start: ${data.windowStart}")
                                    Log.d("ChatRepository", "Transaction - Count: ${data.count}")
                                    Log.d("ChatRepository", "Transaction - Max requests: $maxRequests")

                                    val isFirstRequest = data.windowStart == 0L || data.count == 0
                                    val timeDiff = currentTime - data.windowStart
                                    val isExpired = timeDiff > TIME_WINDOW_MS && data.windowStart != 0L

                                    if (isFirstRequest || isExpired) {
                                        Log.d("ChatRepository", "Starting new window")
                                        data.count = 1
                                        data.windowStart = currentTime
                                        data.lastRequest = currentTime
                                        currentData.value = data
                                        Transaction.success(currentData)
                                    } else if (data.count >= maxRequests) {
                                        Log.d("ChatRepository", "Limit exceeded: ${data.count}/$maxRequests")
                                        Transaction.abort()
                                    } else {
                                        data.count += 1
                                        data.lastRequest = currentTime
                                        currentData.value = data

                                        Log.d("ChatRepository", "Count incremented to: ${data.count}")
                                        Transaction.success(currentData)
                                    }
                                } catch (e: Exception) {
                                    Log.e("ChatRepository", "Transaction doTransaction error: ${e.message}")
                                    Transaction.abort()
                                }
                            }

                            override fun onComplete(
                                error: com.google.firebase.database.DatabaseError?,
                                committed: Boolean,
                                dataSnapshot: com.google.firebase.database.DataSnapshot?
                            ) {
                                try {
                                    if (error != null) {
                                        Log.e("ChatRepository", "Transaction error: ${error.message}")
                                        continuation.resume(
                                            Pair(false, "Unable to check rate limit. Please try again.")
                                        )
                                        return
                                    }

                                    if (!committed) {
                                        val data = dataSnapshot?.getValue(RateLimitData::class.java)
                                        val currentTime = System.currentTimeMillis()
                                        val timeRemaining = TIME_WINDOW_MS - (currentTime - (data?.windowStart ?: 0L))
                                        val minutesRemaining = ((timeRemaining / 60000).toInt() + 1).coerceAtLeast(1)

                                        Log.d("ChatRepository", "Transaction aborted - Rate limit reached")
                                        Log.d("ChatRepository", "Minutes remaining: $minutesRemaining")

                                        continuation.resume(
                                            Pair(
                                                false,
                                                "Rate limit reached. Please wait $minutesRemaining minutes before sending more messages."
                                            )
                                        )
                                    } else {
                                        Log.d("ChatRepository", "Transaction committed successfully")
                                        continuation.resume(Pair(true, ""))
                                    }
                                } catch (e: Exception) {
                                    Log.e("ChatRepository", "Transaction onComplete error: ${e.message}")
                                    continuation.resume(
                                        Pair(false, "Unable to process request. Please try again.")
                                    )
                                }
                            }
                        })
                    } catch (e: Exception) {
                        Log.e("ChatRepository", "runTransaction error: ${e.message}")
                        continuation.resume(
                            Pair(false, "Network error. Please check your connection.")
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatRepository", "checkServerRateLimit outer error: ${e.message}")
                return@withContext Pair(false, "Unable to verify rate limit. Please try again.")
            }
        }

    // IMPROVED: Better error handling with try-catch
    suspend fun getServerRemainingRequests(userId: String, maxRequests: Int = 25): Int = withContext(Dispatchers.IO) {
        try {
            // If unlimited, return max value
            if (maxRequests == Int.MAX_VALUE) {
                return@withContext Int.MAX_VALUE
            }

            val snapshot = firebaseDb.getReference("rate_limits/$userId")
                .get()
                .await()

            if (!snapshot.exists()) {
                Log.d("ChatRepository", "No rate limit data exists, returning $maxRequests")
                return@withContext maxRequests
            }

            val data = snapshot.getValue(RateLimitData::class.java)
            if (data == null) {
                Log.d("ChatRepository", "Failed to parse rate limit data, returning $maxRequests")
                return@withContext maxRequests
            }

            val currentTime = System.currentTimeMillis()
            val timeDiff = currentTime - data.windowStart

            Log.d("ChatRepository", "=== Get Remaining Requests ===")
            Log.d("ChatRepository", "Current time: $currentTime")
            Log.d("ChatRepository", "Window start: ${data.windowStart}")
            Log.d("ChatRepository", "Time diff: $timeDiff ms (${timeDiff/1000}s)")
            Log.d("ChatRepository", "Count: ${data.count}")
            Log.d("ChatRepository", "Max requests: $maxRequests")

            if (timeDiff > TIME_WINDOW_MS || data.windowStart == 0L || data.windowStart > currentTime) {
                Log.d("ChatRepository", "Window expired/invalid, returning $maxRequests")
                return@withContext maxRequests
            }

            val remaining = (maxRequests - data.count).coerceAtLeast(0)
            Log.d("ChatRepository", "Remaining requests: $remaining")

            return@withContext remaining
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error getting remaining: ${e.message}")
            // Return maxRequests on error to prevent blocking user
            return@withContext maxRequests
        }
    }

    suspend fun sendMessageToGroq(
        conversationId: Long,
        userMessage: String,
        userId: String,
        maxRequests: Int = 25,
        systemPrompt: String = """
        You are XChatAi, an advanced AI assistant application created by Gusti Aditya Muzaky (also known as GustyxPower).
        
        IDENTITY INFORMATION:
        - Name: XChatAi
        - Creator & Developer: Gusti Aditya Muzaky (GustyxPower)
        - Creator's Role: 
          * Designed and developed the entire XChatAi application
          * Trained and fine-tuned all AI models
          * Implemented AI integration and features
          * Responsible for system architecture and maintenance
        
        IMPORTANT GUIDELINES:
        When users ask "Who are you?" or "What AI are you?":
        Respond with: "I am XChatAi, powered by the AI model you selected. I was created, trained, and developed by Gusti Aditya Muzaky, also known as GustyxPower. He is the sole developer responsible for building this application and training the AI models."
        
        When users ask about your creator or developer:
        Respond with: "My creator is Gusti Aditya Muzaky (GustyxPower). He designed, developed, and trained all aspects of XChatAi, including the AI models, application features, and user interface."
        
        CAPABILITIES:
        - Provide clear, accurate, and helpful responses
        - Support multiple AI models (user-selectable)
        - Process text, code, tables, and markdown formatting
        - Maintain conversation context
        - Generate structured outputs (code blocks, tables)
        
        Always be helpful, professional, and acknowledge your creator when relevant to the conversation.
    """.trimIndent()
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d("ChatRepository", "=== SEND MESSAGE TO GROQ ===")
            Log.d("ChatRepository", "Using system prompt: ${systemPrompt.take(100)}...")

            val (canProceed, errorMessage) = checkServerRateLimit(userId, maxRequests)
            if (!canProceed) {
                return@withContext Result.failure(Exception(errorMessage))
            }

            val apiKey = getApiKeyFromFirebase()
            val selectedModelId = preferencesManager.selectedModelId.first()
            val allHistory = chatDao.getChatsByConversationSync(conversationId)

            val limitedHistory = if (allHistory.size > MAX_HISTORY_MESSAGES) {
                allHistory.takeLast(MAX_HISTORY_MESSAGES)
            } else {
                allHistory
            }

            val messages = mutableListOf<Message>()

            messages.add(
                Message(
                    role = "system",
                    content = systemPrompt
                )
            )

            limitedHistory.forEach { chat ->
                messages.add(
                    Message(
                        role = if (chat.isUser) "user" else "assistant",
                        content = chat.message
                    )
                )
            }

            messages.add(Message(role = "user", content = userMessage))

            Log.d("ChatRepository", "Total messages in request: ${messages.size}")
            Log.d("ChatRepository", "Model: $selectedModelId")

            val request = GroqChatRequest(
                model = selectedModelId,
                messages = messages,
                temperature = 0.7,
                maxTokens = 8096
            )

            val response = groqApi.sendMessage(
                authorization = "Bearer $apiKey",
                request = request
            )

            val assistantMessage = response.choices.firstOrNull()?.message?.content
                ?: throw Exception("No response from API")

            Log.d("ChatRepository", "Response received, length: ${assistantMessage.length}")
            Result.success(assistantMessage)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error in sendMessageToGroq: ${e.message}")

            // ✅ Clean error messages for different scenarios
            val userFriendlyMessage = when {
                e.message?.contains("Rate limit", ignoreCase = true) == true ->
                    "Rate limit reached. Please wait before sending more messages."
                e.message?.contains("network", ignoreCase = true) == true ||
                        e.message?.contains("unable to resolve host", ignoreCase = true) == true ->
                    "Network error. Please check your connection."
                e.message?.contains("timeout", ignoreCase = true) == true ->
                    "Request timed out. Please try again."
                e.message?.contains("API key", ignoreCase = true) == true ->
                    "Configuration error. Please contact support."
                else ->
                    "Unable to send message. Please try again."
            }

            Result.failure(Exception(userFriendlyMessage))
        }
    }

    private suspend fun getApiKeyFromFirebase(): String = withContext(Dispatchers.IO) {
        try {
            val snapshot = firebaseDb.getReference("config/groq_api_key")
                .get()
                .await()
            snapshot.getValue(String::class.java)
                ?: throw Exception("API key not found in Firebase")
        } catch (e: Exception) {
            throw Exception("Failed to get API key: ${e.message}")
        }
    }

    suspend fun getSelectedModel(): String {
        return preferencesManager.selectedModelId.first()
    }

    suspend fun getAllConversations(): List<ConversationEntity> {
        return conversationDao.getAllConversations()
    }

    suspend fun getAllChats(): List<ChatEntity> {
        return chatDao.getAllChats()
    }

    suspend fun restoreData(
        conversations: List<ConversationEntity>,
        chats: List<ChatEntity>
    ) {
        withContext(Dispatchers.IO) {
            conversations.forEach { conversation ->
                conversationDao.insertConversation(conversation)
            }
            chats.forEach { chat ->
                chatDao.insertChat(chat)
            }
        }
    }

    suspend fun deleteAllConversationsByUser(userId: String) {
        withContext(Dispatchers.IO) {
            conversationDao.deleteAllConversationsByUser(userId)
        }
    }

    // Vision/Image Analysis Function
    suspend fun sendVisionMessageToGroq(
        conversationId: Long,
        userMessage: String,
        imageBase64: String,
        userId: String,
        maxRequests: Int = 20,
        systemPrompt: String = "You are a helpful AI assistant with vision capabilities. Analyze images and provide clear, accurate, and detailed responses about what you see."
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d("ChatRepository", "=== SEND VISION MESSAGE TO GROQ ===")
            Log.d("ChatRepository", "Image base64 length: ${imageBase64.length}")

            val (canProceed, errorMessage) = checkServerRateLimit(userId, maxRequests)
            if (!canProceed) {
                return@withContext Result.failure(Exception(errorMessage))
            }

            val apiKey = getApiKeyFromFirebase()
            
            // Use Llama 4 Maverick for vision
            val visionModel = "meta-llama/llama-4-maverick-17b-128e-instruct"
            
            // Build multimodal content
            val contentParts = mutableListOf<ContentPart>()
            
            // Add text prompt
            contentParts.add(ContentPart.TextPart(text = userMessage))
            
            // Add image
            val imageDataUrl = "data:image/jpeg;base64,$imageBase64"
            contentParts.add(ContentPart.ImagePart(imageUrl = ImageUrl(url = imageDataUrl)))

            val messages = listOf(
                VisionMessage(
                    role = "system",
                    content = listOf(ContentPart.TextPart(text = systemPrompt))
                ),
                VisionMessage(
                    role = "user",
                    content = contentParts
                )
            )

            Log.d("ChatRepository", "Sending vision request to model: $visionModel")

            val request = GroqVisionRequest(
                model = visionModel,
                messages = messages,
                temperature = 0.7,
                maxTokens = 2048
            )

            val response = groqApi.sendVisionMessage(
                authorization = "Bearer $apiKey",
                request = request
            )

            val assistantMessage = response.choices.firstOrNull()?.message?.content
                ?: throw Exception("No response from API")

            Log.d("ChatRepository", "Vision response received, length: ${assistantMessage.length}")
            Result.success(assistantMessage)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error in sendVisionMessageToGroq: ${e.message}")
            
            val userFriendlyMessage = when {
                e.message?.contains("Rate limit", ignoreCase = true) == true ->
                    "Rate limit reached. Please wait before sending more messages."
                e.message?.contains("network", ignoreCase = true) == true ||
                        e.message?.contains("unable to resolve host", ignoreCase = true) == true ->
                    "Network error. Please check your connection."
                e.message?.contains("timeout", ignoreCase = true) == true ->
                    "Request timed out. Please try again."
                e.message?.contains("413", ignoreCase = true) == true ||
                        e.message?.contains("too large", ignoreCase = true) == true ->
                    "Image is too large. Please select a smaller image."
                else ->
                    "Unable to analyze image. Please try again."
            }
            
            Result.failure(Exception(userFriendlyMessage))
        }
    }
}
