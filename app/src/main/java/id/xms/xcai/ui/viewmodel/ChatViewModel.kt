package id.xms.xcai.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import id.xms.xcai.data.local.ChatEntity
import id.xms.xcai.data.local.ConversationEntity
import id.xms.xcai.data.model.ResponseMode
import id.xms.xcai.data.preferences.UserPreferences
import id.xms.xcai.data.repository.ChatRepository
import id.xms.xcai.data.repository.PremiumManager
import id.xms.xcai.data.repository.PremiumStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class ChatUiState(
    val messages: List<ChatEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentConversationId: Long? = null,
    val remainingRequests: Int = 20,
    val isLoadingCounter: Boolean = false,
    val streamingText: String = "",
    val isStreaming: Boolean = false,
    val isThinking: Boolean = false,
    val currentResponseMode: ResponseMode = ResponseMode.CHAT,
    val selectedImageUri: String? = null,  // For image preview
    val selectedImageBase64: String? = null, // For sending to API
    // STT State
    val isRecording: Boolean = false,
    val isTranscribing: Boolean = false,
    val transcribedText: String? = null
)

data class ConversationUiState(
    val conversations: List<ConversationEntity> = emptyList(),
    val isLoading: Boolean = false
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application)
    private val premiumManager = PremiumManager()
    private val firebaseDb = FirebaseDatabase.getInstance()
    private val preferencesManager = UserPreferences(application)

    private val _chatUiState = MutableStateFlow(ChatUiState())
    val chatUiState: StateFlow<ChatUiState> = _chatUiState.asStateFlow()

    private val _conversationUiState = MutableStateFlow(ConversationUiState())
    val conversationUiState: StateFlow<ConversationUiState> = _conversationUiState.asStateFlow()

    private val _premiumStatus = MutableStateFlow(PremiumStatus())
    val premiumStatus: StateFlow<PremiumStatus> = _premiumStatus.asStateFlow()

    private var rateLimitListener: ValueEventListener? = null
    private var currentUserId: String? = null
    private var pollingJob: Job? = null
    private var isUserTyping = false
    private var chatCollectionJob: Job? = null
    private var streamingJob: Job? = null
    private var premiumJob: Job? = null
    private var responseModeJob: Job? = null
    private var customPromptJob: Job? = null
    
    // Custom prompt for CUSTOM response mode
    private var _customPrompt = ""

    init {
        responseModeJob = viewModelScope.launch {
            preferencesManager.responseMode.collect { mode ->
                _chatUiState.value = _chatUiState.value.copy(currentResponseMode = mode)
                Log.d("ChatViewModel", "Response mode changed to: ${mode.displayName}")
            }
        }
        
        customPromptJob = viewModelScope.launch {
            preferencesManager.customPrompt.collect { prompt ->
                _customPrompt = prompt
                Log.d("ChatViewModel", "Custom prompt loaded: ${prompt.take(50)}...")
            }
        }
    }

    fun setupRateLimitListener(userId: String) {
        Log.d("ChatViewModel", "=== SETUP LISTENER ===")
        currentUserId = userId

        rateLimitListener?.let {
            firebaseDb.getReference("rate_limits/$userId").removeEventListener(it)
        }

        pollingJob?.cancel()

        val userEmail = FirebaseAuth.getInstance().currentUser?.email

        if (userEmail != null) {
            viewModelScope.launch {
                try {
                    val status = premiumManager.checkPremiumStatus(userEmail)
                    _premiumStatus.value = status

                    val maxReq = premiumManager.getMaxRequests(status)
                    Log.d("ChatViewModel", "User is ${status.tier}, max requests: $maxReq")
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Error checking premium status: ${e.message}")
                }
            }

            premiumJob?.cancel()
            premiumJob = viewModelScope.launch {
                try {
                    premiumManager.observePremiumStatus(userEmail).collect { status ->
                        _premiumStatus.value = status
                        Log.d("ChatViewModel", "Premium status updated: ${status.tier}")
                    }
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Error observing premium status: ${e.message}")
                }
            }
        }

        val rateLimitRef = firebaseDb.getReference("rate_limits/$userId")
        loadRemainingRequests(userId)

        rateLimitListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val maxRequests = premiumManager.getMaxRequests(_premiumStatus.value)

                    if (!snapshot.exists()) {
                        _chatUiState.value = _chatUiState.value.copy(
                            remainingRequests = maxRequests,
                            isLoadingCounter = false
                        )
                        return
                    }

                    val currentTime = System.currentTimeMillis()
                    val count = snapshot.child("count").getValue(Int::class.java) ?: 0
                    val windowStart = snapshot.child("windowStart").getValue(Long::class.java) ?: 0L
                    val timeDiff = currentTime - windowStart
                    val isExpired = timeDiff > 30 * 60 * 1000L

                    val remaining = if (isExpired || windowStart == 0L) {
                        maxRequests
                    } else {
                        (maxRequests - count).coerceAtLeast(0)
                    }

                    _chatUiState.value = _chatUiState.value.copy(
                        remainingRequests = remaining,
                        isLoadingCounter = false
                    )
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Error in onDataChange: ${e.message}")
                    val maxRequests = premiumManager.getMaxRequests(_premiumStatus.value)
                    _chatUiState.value = _chatUiState.value.copy(
                        remainingRequests = maxRequests,
                        isLoadingCounter = false
                    )
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatViewModel", "Rate limit listener cancelled: ${error.message}")
                val maxRequests = premiumManager.getMaxRequests(_premiumStatus.value)
                _chatUiState.value = _chatUiState.value.copy(
                    remainingRequests = maxRequests,
                    isLoadingCounter = false
                )
            }
        }

        try {
            rateLimitRef.addValueEventListener(rateLimitListener!!)
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error adding rate limit listener: ${e.message}")
        }

        startPolling(userId)
    }

    private fun startPolling(userId: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            delay(5000)
            while(isActive) {
                try {
                    if (!isUserTyping) {
                        loadRemainingRequests(userId)
                    }
                    delay(10000)
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Polling error: ${e.message}")
                    delay(10000)
                }
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
    }

    fun setUserTyping(typing: Boolean) {
        isUserTyping = typing
    }

    fun loadRemainingRequests(userId: String) {
        viewModelScope.launch {
            try {
                _chatUiState.value = _chatUiState.value.copy(isLoadingCounter = true)
                val maxRequests = premiumManager.getMaxRequests(_premiumStatus.value)
                val remaining = repository.getServerRemainingRequests(userId, maxRequests)
                _chatUiState.value = _chatUiState.value.copy(
                    remainingRequests = remaining,
                    isLoadingCounter = false
                )
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error loading rate limit: ${e.message}")
                val maxRequests = premiumManager.getMaxRequests(_premiumStatus.value)
                _chatUiState.value = _chatUiState.value.copy(
                    remainingRequests = maxRequests,
                    isLoadingCounter = false
                )
            }
        }
    }

    fun loadConversations(userId: String) {
        viewModelScope.launch {
            try {
                _conversationUiState.value = _conversationUiState.value.copy(isLoading = true)
                repository.getConversationsByUser(userId).collect { conversations ->
                    _conversationUiState.value = ConversationUiState(
                        conversations = conversations,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error loading conversations: ${e.message}")
                _conversationUiState.value = _conversationUiState.value.copy(isLoading = false)
            }
        }
        setupRateLimitListener(userId)
    }

    fun loadChats(conversationId: Long) {
        Log.d("ChatViewModel", "=== LOAD CHATS ===")
        Log.d("ChatViewModel", "Loading conversation: $conversationId")

        chatCollectionJob?.cancel()

        chatCollectionJob = viewModelScope.launch {
            _chatUiState.value = _chatUiState.value.copy(
                currentConversationId = conversationId,
                isLoading = true
            )

            try {
                repository.getChatsByConversation(conversationId).collect { chats ->
                    if (_chatUiState.value.currentConversationId == conversationId) {
                        _chatUiState.value = _chatUiState.value.copy(
                            messages = chats,
                            isLoading = false
                        )
                        Log.d("ChatViewModel", "Messages loaded: ${chats.size}")
                    }
                }
            } catch (e: CancellationException) {
                // Normal cancellation when switching conversations, don't show error
                Log.d("ChatViewModel", "Chat collection cancelled (normal behavior)")
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error loading chats: ${e.message}")
                _chatUiState.value = _chatUiState.value.copy(
                    isLoading = false,
                    error = "Failed to load conversation"
                )
            }
        }
    }

    fun sendMessage(userId: String, message: String) {
        viewModelScope.launch {
            try {
                Log.d("ChatViewModel", "=== SEND MESSAGE ===")
                Log.d("ChatViewModel", "Message: $message")
                Log.d("ChatViewModel", "Current conversation: ${_chatUiState.value.currentConversationId}")

                val responseMode = _chatUiState.value.currentResponseMode
                Log.d("ChatViewModel", "Using response mode: ${responseMode.displayName}")

                _chatUiState.value = _chatUiState.value.copy(
                    isLoading = true,
                    error = null,
                    isThinking = false
                )

                var conversationId = _chatUiState.value.currentConversationId

                if (conversationId == null) {
                    Log.d("ChatViewModel", "Creating new conversation...")
                    conversationId = repository.createConversation(
                        userId = userId,
                        title = message.take(50)
                    )
                    Log.d("ChatViewModel", "New conversation created: $conversationId")

                    _chatUiState.value = _chatUiState.value.copy(currentConversationId = conversationId)
                    loadChats(conversationId)
                }

                repository.insertChat(
                    ChatEntity(
                        conversationId = conversationId,
                        message = message,
                        isUser = true
                    )
                )

                _chatUiState.value = _chatUiState.value.copy(isThinking = true)

                val maxRequests = premiumManager.getMaxRequests(_premiumStatus.value)
                
                // Use custom prompt if CUSTOM mode is selected
                val effectiveSystemPrompt = if (responseMode == ResponseMode.CUSTOM && _customPrompt.isNotBlank()) {
                    "IDENTITY: XChatAi by Gusti Aditya Muzaky (GustyxPower).\n\n${_customPrompt}"
                } else {
                    responseMode.systemPrompt
                }

                val result = repository.sendMessageToGroq(
                    conversationId = conversationId,
                    userMessage = message,
                    userId = userId,
                    maxRequests = maxRequests,
                    systemPrompt = effectiveSystemPrompt
                )

                result.onSuccess { aiResponse ->
                    Log.d("ChatViewModel", "API success, starting typewriter effect")

                    _chatUiState.value = _chatUiState.value.copy(
                        isThinking = false,
                        isLoading = false
                    )

                    startTypewriterEffect(conversationId, aiResponse)
                    loadRemainingRequests(userId)

                }.onFailure { exception ->
                    Log.e("ChatViewModel", "API failed: ${exception.message}")

                    val userMessage = when {
                        exception.message?.contains("Rate limit", ignoreCase = true) == true -> {
                            exception.message ?: "Rate limit reached"
                        }
                        exception.message?.contains("network", ignoreCase = true) == true ||
                                exception.message?.contains("unable to resolve", ignoreCase = true) == true -> {
                            "Network error. Please check your connection."
                        }
                        exception.message?.contains("timeout", ignoreCase = true) == true -> {
                            "Request timed out. Please try again."
                        }
                        else -> {
                            "Unable to send message. Please try again."
                        }
                    }

                    _chatUiState.value = _chatUiState.value.copy(
                        isLoading = false,
                        isThinking = false,
                        error = userMessage
                    )

                    loadRemainingRequests(userId)
                }

            } catch (e: Exception) {
                Log.e("ChatViewModel", "Unexpected error in sendMessage: ${e.message}", e)

                _chatUiState.value = _chatUiState.value.copy(
                    isLoading = false,
                    isThinking = false,
                    error = "An unexpected error occurred. Please try again."
                )

                loadRemainingRequests(userId)
            }
        }
    }

    /**
     * Edit user message and resend to AI.
     * Deletes the old user message and AI response, then sends edited message.
     */
    fun editAndResendMessage(userId: String, originalMessage: ChatEntity, newMessageText: String) {
        viewModelScope.launch {
            try {
                Log.d("ChatViewModel", "=== EDIT AND RESEND MESSAGE ===")
                Log.d("ChatViewModel", "Original: ${originalMessage.message.take(50)}")
                Log.d("ChatViewModel", "New: ${newMessageText.take(50)}")

                val conversationId = originalMessage.conversationId

                // Delete original user message and subsequent AI response
                repository.deleteMessagesFromTimestamp(conversationId, originalMessage.timestamp)
                
                // Reload chats to reflect deletion
                loadChats(conversationId)
                
                // Send the edited message
                sendMessage(userId, newMessageText)

            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error in editAndResendMessage: ${e.message}", e)
                _chatUiState.value = _chatUiState.value.copy(
                    error = "Failed to edit message"
                )
            }
        }
    }

    /**
     * Send message with document context.
     * Display only file indicator in chat, but send full content to AI.
     */
    fun sendDocumentMessage(userId: String, userQuestion: String, documentName: String, documentContent: String) {
        viewModelScope.launch {
            try {
                Log.d("ChatViewModel", "=== SEND DOCUMENT MESSAGE ===")
                Log.d("ChatViewModel", "Document: $documentName")
                Log.d("ChatViewModel", "Question: $userQuestion")

                val responseMode = _chatUiState.value.currentResponseMode

                _chatUiState.value = _chatUiState.value.copy(
                    isLoading = true,
                    error = null,
                    isThinking = false
                )

                var conversationId = _chatUiState.value.currentConversationId

                if (conversationId == null) {
                    conversationId = repository.createConversation(
                        userId = userId,
                        title = "📄 $documentName"
                    )
                    _chatUiState.value = _chatUiState.value.copy(currentConversationId = conversationId)
                    loadChats(conversationId)
                }

                // Display message: only show file indicator + question
                val displayMessage = "📄 $documentName\n\n$userQuestion"
                
                // AI context: include full document content
                val aiContext = "Here is the content of the file '$documentName':\n\n```\n$documentContent\n```\n\nUser's question: $userQuestion"

                // Save display message to chat
                repository.insertChat(
                    ChatEntity(
                        conversationId = conversationId,
                        message = displayMessage,
                        isUser = true
                    )
                )

                _chatUiState.value = _chatUiState.value.copy(isThinking = true)

                val maxRequests = premiumManager.getMaxRequests(_premiumStatus.value)
                
                // Use custom prompt if CUSTOM mode is selected
                val effectiveSystemPrompt = if (responseMode == ResponseMode.CUSTOM && _customPrompt.isNotBlank()) {
                    "IDENTITY: XChatAi by Gusti Aditya Muzaky (GustyxPower).\n\n${_customPrompt}"
                } else {
                    responseMode.systemPrompt
                }

                // Send AI context (with full document) to API
                val result = repository.sendMessageToGroq(
                    conversationId = conversationId,
                    userMessage = aiContext,
                    userId = userId,
                    maxRequests = maxRequests,
                    systemPrompt = effectiveSystemPrompt
                )

                result.onSuccess { aiResponse ->
                    _chatUiState.value = _chatUiState.value.copy(
                        isThinking = false,
                        isLoading = false
                    )
                    startTypewriterEffect(conversationId, aiResponse)
                    loadRemainingRequests(userId)
                }.onFailure { exception ->
                    Log.e("ChatViewModel", "API failed: ${exception.message}")
                    _chatUiState.value = _chatUiState.value.copy(
                        isLoading = false,
                        isThinking = false,
                        error = exception.message ?: "An error occurred"
                    )
                    loadRemainingRequests(userId)
                }

            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error in sendDocumentMessage: ${e.message}", e)
                _chatUiState.value = _chatUiState.value.copy(
                    isLoading = false,
                    isThinking = false,
                    error = "An unexpected error occurred."
                )
            }
        }
    }

    private fun startTypewriterEffect(conversationId: Long, fullText: String) {
        streamingJob?.cancel()

        streamingJob = viewModelScope.launch {
            try {
                _chatUiState.value = _chatUiState.value.copy(
                    isStreaming = true,
                    streamingText = ""
                )

                val delayPerChar = 20L

                fullText.forEachIndexed { index, _ ->
                    if (isActive) {
                        _chatUiState.value = _chatUiState.value.copy(
                            streamingText = fullText.substring(0, index + 1)
                        )
                        delay(delayPerChar)
                    }
                }

                if (isActive) {
                    repository.insertChat(
                        ChatEntity(
                            conversationId = conversationId,
                            message = fullText,
                            isUser = false
                        )
                    )

                    repository.getConversationById(conversationId)?.let { conversation ->
                        repository.updateConversation(
                            conversation.copy(updatedAt = System.currentTimeMillis())
                        )
                    }

                    _chatUiState.value = _chatUiState.value.copy(
                        isStreaming = false,
                        streamingText = ""
                    )
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error in typewriter effect: ${e.message}")
                _chatUiState.value = _chatUiState.value.copy(
                    isStreaming = false,
                    streamingText = ""
                )
            }
        }
    }

    fun stopStreaming() {
        streamingJob?.cancel()
        _chatUiState.value = _chatUiState.value.copy(
            isStreaming = false,
            streamingText = ""
        )
    }

    fun deleteConversation(conversation: ConversationEntity) {
        viewModelScope.launch {
            try {
                repository.deleteConversation(conversation)
                if (_chatUiState.value.currentConversationId == conversation.id) {
                    clearCurrentConversation()
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error deleting conversation: ${e.message}")
            }
        }
    }

    fun renameConversation(conversation: ConversationEntity, newTitle: String) {
        viewModelScope.launch {
            try {
                Log.d("ChatViewModel", "Renaming conversation ${conversation.id} to: $newTitle")
                repository.updateConversation(
                    conversation.copy(
                        title = newTitle.trim(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error renaming conversation: ${e.message}")
            }
        }
    }

    /**
     * Edit user message and regenerate AI response
     */
    fun editMessageAndRegenerate(userId: String, messageId: String, newMessage: String) {
        viewModelScope.launch {
            try {
                val messageIdLong = messageId.toLongOrNull() ?: return@launch
                val conversationId = _chatUiState.value.currentConversationId ?: return@launch

                val messages = _chatUiState.value.messages
                val messageIndex = messages.indexOfFirst { it.id == messageIdLong }

                if (messageIndex == -1 || !messages[messageIndex].isUser) return@launch

                // Delete AI responses after this user message
                val messagesToDelete = messages.subList(messageIndex + 1, messages.size)

                messagesToDelete.forEach { msg ->
                    if (!msg.isUser) {
                        repository.deleteChat(msg)  // ✅ Call repository directly
                    }
                }

                // Update user message
                val updatedMessage = messages[messageIndex].copy(message = newMessage)
                repository.updateChat(updatedMessage)  // ✅ Call repository directly

                // Send new message to AI
                sendMessage(userId, newMessage)

            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error editing message: ${e.message}")
                _chatUiState.value = _chatUiState.value.copy(
                    error = "Failed to edit message"
                )
            }
        }
    }

    /**
     * Regenerate the last AI response
     */
    fun regenerateResponse(userId: String) {
        viewModelScope.launch {
            try {
                val conversationId = _chatUiState.value.currentConversationId ?: return@launch
                val messages = _chatUiState.value.messages

                // Find last user message
                val lastUserMessage = messages.lastOrNull { it.isUser } ?: return@launch

                // Delete last AI response if exists
                val lastAiMessage = messages.lastOrNull { !it.isUser }
                if (lastAiMessage != null) {
                    repository.deleteChat(lastAiMessage)  // ✅ Call repository directly
                }

                // Resend the last user message
                sendMessage(userId, lastUserMessage.message)

            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error regenerating response: ${e.message}")
                _chatUiState.value = _chatUiState.value.copy(
                    error = "Failed to regenerate response"
                )
            }
        }
    }

    // ✅ REMOVED: Extension functions are gone!
    // Functions now call repository directly

    fun clearError() {
        _chatUiState.value = _chatUiState.value.copy(error = null)
    }

    // Image handling functions
    fun setSelectedImage(uri: String?, base64: String?) {
        _chatUiState.value = _chatUiState.value.copy(
            selectedImageUri = uri,
            selectedImageBase64 = base64
        )
    }

    fun clearSelectedImage() {
        _chatUiState.value = _chatUiState.value.copy(
            selectedImageUri = null,
            selectedImageBase64 = null
        )
    }

    fun sendImageMessage(userId: String, message: String, imageBase64: String, imageUri: String) {
        viewModelScope.launch {
            try {
                Log.d("ChatViewModel", "=== SEND IMAGE MESSAGE ===")
                Log.d("ChatViewModel", "Message: $message")
                Log.d("ChatViewModel", "Image base64 length: ${imageBase64.length}")

                _chatUiState.value = _chatUiState.value.copy(
                    isLoading = true,
                    error = null,
                    isThinking = false
                )

                var conversationId = _chatUiState.value.currentConversationId

                if (conversationId == null) {
                    Log.d("ChatViewModel", "Creating new conversation for image...")
                    conversationId = repository.createConversation(
                        userId = userId,
                        title = "Image: ${message.take(30)}"
                    )
                    _chatUiState.value = _chatUiState.value.copy(currentConversationId = conversationId)
                    loadChats(conversationId)
                }

                // Insert user message with image reference
                repository.insertChat(
                    ChatEntity(
                        conversationId = conversationId,
                        message = message,
                        isUser = true,
                        imageUri = imageUri
                    )
                )

                // Clear selected image after sending
                clearSelectedImage()

                _chatUiState.value = _chatUiState.value.copy(isThinking = true)

                val maxRequests = premiumManager.getMaxRequests(_premiumStatus.value)

                val result = repository.sendVisionMessageToGroq(
                    conversationId = conversationId,
                    userMessage = message,
                    imageBase64 = imageBase64,
                    userId = userId,
                    maxRequests = maxRequests
                )

                result.onSuccess { aiResponse ->
                    Log.d("ChatViewModel", "Vision API success")

                    _chatUiState.value = _chatUiState.value.copy(
                        isThinking = false,
                        isLoading = false
                    )

                    startTypewriterEffect(conversationId, aiResponse)
                    loadRemainingRequests(userId)

                }.onFailure { exception ->
                    Log.e("ChatViewModel", "Vision API failed: ${exception.message}")

                    _chatUiState.value = _chatUiState.value.copy(
                        isLoading = false,
                        isThinking = false,
                        error = exception.message ?: "Unable to analyze image"
                    )

                    loadRemainingRequests(userId)
                }

            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error in sendImageMessage: ${e.message}", e)

                _chatUiState.value = _chatUiState.value.copy(
                    isLoading = false,
                    isThinking = false,
                    error = "An unexpected error occurred. Please try again."
                )

                loadRemainingRequests(userId)
            }
        }
    }

    fun clearCurrentConversation() {
        Log.d("ChatViewModel", "=== CLEAR CONVERSATION ===")

        chatCollectionJob?.cancel()
        chatCollectionJob = null
        stopStreaming()

        _chatUiState.value = _chatUiState.value.copy(
            messages = emptyList(),
            currentConversationId = null,
            error = null,
            isLoading = false,
            isStreaming = false,
            streamingText = "",
            isThinking = false
        )
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("ChatViewModel", "=== ON CLEARED ===")

        try {
            stopPolling()
            chatCollectionJob?.cancel()
            streamingJob?.cancel()
            premiumJob?.cancel()
            responseModeJob?.cancel()

            rateLimitListener?.let { listener ->
                currentUserId?.let { userId ->
                    firebaseDb.getReference("rate_limits/$userId")
                        .removeEventListener(listener)
                }
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error in onCleared: ${e.message}")
        }
    }

    // ====== Speech-to-Text State (using Android SpeechRecognizer in UI) ======

    fun setRecording(isRecording: Boolean) {
        _chatUiState.value = _chatUiState.value.copy(isRecording = isRecording)
    }

    fun clearTranscribedText() {
        _chatUiState.value = _chatUiState.value.copy(transcribedText = null)
    }
}
