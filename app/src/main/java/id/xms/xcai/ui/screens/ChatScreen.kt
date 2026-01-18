package id.xms.xcai.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import id.xms.xcai.R
import id.xms.xcai.data.local.ChatEntity
import id.xms.xcai.data.util.SpeechRecognizerHelper
import id.xms.xcai.data.util.DocumentReader
import id.xms.xcai.ui.components.AIThinkingIndicator
import id.xms.xcai.ui.components.AITypingIndicator
import id.xms.xcai.ui.components.GlassCard
import id.xms.xcai.ui.components.MessageItem
import id.xms.xcai.ui.components.StreamingMessageItem
import id.xms.xcai.ui.theme.Web3Black
import id.xms.xcai.ui.theme.Web3Cyan
import id.xms.xcai.ui.theme.Web3MidnightBlue
import id.xms.xcai.ui.theme.Web3Slate
import id.xms.xcai.ui.theme.Web3TextPrimary
import id.xms.xcai.ui.theme.Web3TextSecondary
import id.xms.xcai.ui.viewmodel.AuthViewModel
import id.xms.xcai.ui.viewmodel.ChatViewModel
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.scale
import androidx.compose.material3.CircularProgressIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
        chatViewModel: ChatViewModel,
        authViewModel: AuthViewModel,
        onOpenDrawer: () -> Unit,
        modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()

    val chatUiState by chatViewModel.chatUiState.collectAsState()
    val premiumStatus by chatViewModel.premiumStatus.collectAsState()
    val authUiState by authViewModel.authUiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var messageText by remember { mutableStateOf("") }
    var showRateLimitDialog by remember { mutableStateOf(false) }
    var showLowQuotaWarning by remember { mutableStateOf(false) }
    var rateLimitMessage by remember { mutableStateOf("") }

    // Edit message dialog state
    var showEditDialog by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<ChatEntity?>(null) }
    var editingMessageText by remember { mutableStateOf("") }

    val context = LocalContext.current

    // Image picker launcher
    val imagePickerLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) {
                    uri: Uri? ->
                uri?.let { imageUri ->
                    scope.launch {
                        processImageUri(context, imageUri, chatViewModel, snackbarHostState)
                    }
                }
            }

    // Camera photo URI
    var cameraPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // Camera launcher
    val cameraLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.TakePicture()) {
                    success ->
                if (success && cameraPhotoUri != null) {
                    scope.launch {
                        processImageUri(context, cameraPhotoUri!!, chatViewModel, snackbarHostState)
                    }
                }
            }

    // Document state
    var selectedDocumentName by remember { mutableStateOf<String?>(null) }
    var selectedDocumentContent by remember { mutableStateOf<String?>(null) }

    // Document upload toast/pill popup
    var showDocumentToast by remember { mutableStateOf(false) }
    var documentToastMessage by remember { mutableStateOf("") }

    // Document picker launcher
    val documentPickerLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) {
                    uri: Uri? ->
                uri?.let { docUri ->
                    scope.launch {
                        withContext(Dispatchers.IO) { DocumentReader.readDocument(context, docUri) }
                                .onSuccess { docContent ->
                                    selectedDocumentName = docContent.fileName
                                    selectedDocumentContent = docContent.content
                                    // Show pill popup instead of snackbar
                                    documentToastMessage = docContent.fileName
                                    showDocumentToast = true
                                    // Auto hide after 2 seconds
                                    delay(2000)
                                    showDocumentToast = false
                                }
                                .onFailure { error ->
                                    snackbarHostState.showSnackbar(
                                            error.message ?: "Failed to read document"
                                    )
                                }
                    }
                }
            }

    // Bottom sheet state for image source picker
    var showImageSourceSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // Speech Recognition state (STT) - using Android's built-in SpeechRecognizer
    var isRecording by remember { mutableStateOf(false) }
    var sttError by remember { mutableStateOf<String?>(null) }
    
    // Speech recognizer helper
    val speechRecognizerHelper = remember {
        SpeechRecognizerHelper(
            context = context,
            onResult = { transcribedText ->
                // Directly set the message text
                messageText = transcribedText
                isRecording = false
                chatViewModel.setRecording(false)
            },
            onError = { errorMessage ->
                sttError = errorMessage
                isRecording = false
                chatViewModel.setRecording(false)
            },
            onListening = { listening ->
                isRecording = listening
                chatViewModel.setRecording(listening)
            }
        )
    }
    
    // Show STT error as snackbar
    LaunchedEffect(sttError) {
        sttError?.let { error ->
            snackbarHostState.showSnackbar(error)
            sttError = null
        }
    }
    
    // Permission launcher for RECORD_AUDIO
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Start speech recognition when permission granted
            speechRecognizerHelper.startListening("id-ID")
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Izin mikrofon diperlukan untuk fitur voice input")
            }
        }
    }

    LaunchedEffect(messageText) { chatViewModel.setUserTyping(messageText.isNotEmpty()) }

    DisposableEffect(Unit) { 
        onDispose { 
            chatViewModel.setUserTyping(false) 
            speechRecognizerHelper.cancel()
        } 
    }

    LaunchedEffect(chatUiState.remainingRequests, chatUiState.isLoadingCounter) {
        if (!chatUiState.isLoadingCounter &&
                        chatUiState.remainingRequests == 0 &&
                        !premiumStatus.isPremium
        ) {
            rateLimitMessage =
                    "You've reached the maximum of 20 requests per 30 minutes. Please wait before sending more messages."
            showRateLimitDialog = true
        }
    }

    LaunchedEffect(chatUiState.messages.size) {
        if (chatUiState.messages.isNotEmpty() && !chatUiState.isStreaming) {
            delay(100)
            val targetIndex = (chatUiState.messages.size - 1).coerceAtLeast(0)
            try {
                listState.animateScrollToItem(targetIndex)
            } catch (e: Exception) {
                // Ignore scroll errors
            }
        }
    }

    LaunchedEffect(chatUiState.error) {
        chatUiState.error?.let { error ->
            if (error.contains("rate limit", ignoreCase = true) ||
                            error.contains("request limit", ignoreCase = true) ||
                            error.contains("wait", ignoreCase = true)
            ) {

                rateLimitMessage =
                        if (premiumStatus.isPremium) {
                            "You've reached your ${premiumStatus.maxRequests} requests limit. Please wait 30 minutes before sending more messages."
                        } else {
                            error
                        }
                showRateLimitDialog = true
            } else {
                snackbarHostState.showSnackbar(message = error, duration = SnackbarDuration.Short)
            }

            chatViewModel.clearError()
        }
    }

    LaunchedEffect(chatUiState.remainingRequests) {
        if (chatUiState.remainingRequests in 1..5 &&
                        !showLowQuotaWarning &&
                        !chatUiState.isLoadingCounter &&
                        !premiumStatus.isPremium
        ) {
            showLowQuotaWarning = true
        }
    }

    Box(
            modifier =
                    modifier.fillMaxSize()
                            .background(
                                    brush =
                                            Brush.verticalGradient(
                                                    colors = listOf(Web3MidnightBlue, Web3Black)
                                            )
                            )
    ) {
        Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                // 0. Welcome View (Empty State)
                if (chatUiState.messages.isEmpty() && !chatUiState.isLoading) {
                    WelcomeView(onSuggestionClick = { suggestion -> messageText = suggestion })
                }

                // 1. Message List
                LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().imePadding(),
                        contentPadding =
                                PaddingValues(
                                        top = 100.dp,
                                        bottom = 120.dp,
                                        start = 16.dp,
                                        end = 16.dp
                                ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(items = chatUiState.messages, key = { it.id }) { message ->
                        MessageItem(
                                message = message,
                                onEdit = { msg ->
                                    if (!msg.message.startsWith("📄")) {
                                        editingMessage = msg
                                        editingMessageText = msg.message
                                        showEditDialog = true
                                    }
                                }
                        )
                    }

                    if (chatUiState.isStreaming && chatUiState.streamingText.isNotEmpty()) {
                        item(key = "streaming_message") {
                            StreamingMessageItem(text = chatUiState.streamingText)
                        }
                    }

                    if (chatUiState.isThinking) {
                        item(key = "thinking_indicator") { AIThinkingIndicator() }
                    } else if (chatUiState.isLoading &&
                                    !chatUiState.isStreaming &&
                                    chatUiState.messages.isNotEmpty()
                    ) {
                        item(key = "typing_indicator") { AITypingIndicator() }
                    }
                }

                // 2. Floating Header
                GlassCard(
                        modifier =
                                Modifier.align(Alignment.TopCenter)
                                        .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                                        .fillMaxWidth()
                                        .height(70.dp),
                        backgroundColor = Web3MidnightBlue.copy(alpha = 0.8f),
                        borderColor = Web3Cyan.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(100.dp) // Capsule shape
                ) {
                    Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Default.Menu, "Menu", tint = Web3TextPrimary)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                    "XChatAi",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Web3TextPrimary
                            )
                            // Subtitle / Status
                            Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (chatUiState.isLoadingCounter) {
                                    Text(
                                            "Syncing...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Web3Cyan
                                    )
                                } else {
                                    Text(
                                            if (premiumStatus.isPremium) "Premium Active"
                                            else "${chatUiState.remainingRequests} requests left",
                                            style = MaterialTheme.typography.labelSmall,
                                            color =
                                                    if (premiumStatus.isPremium) Web3Cyan
                                                    else Web3TextSecondary
                                    )
                                }
                            }
                        }

                        if (premiumStatus.isPremium) {
                            Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Web3Cyan.copy(alpha = 0.2f)
                            ) {
                                Text(
                                        text = "PRO",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Web3Cyan,
                                        modifier =
                                                Modifier.padding(
                                                        horizontal = 10.dp,
                                                        vertical = 4.dp
                                                )
                                )
                            }
                        }
                    }
                }

                // 3. Floating Input Area
                Box(
                        modifier =
                                Modifier.align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                                        .imePadding()
                ) {
                    // Input Content (Refactored below)
                    // We will use the existing input logic but wrapped in new design
                    FloatingInputBar(
                            messageText = messageText,
                            onMessageChange = {
                                messageText = it
                                chatViewModel.setUserTyping(it.isNotEmpty())
                            },
                            onImagePick = { showImageSourceSheet = true },
                            onSend = {
                                // Send Logic
                                if (chatUiState.isStreaming) {
                                    chatViewModel.stopStreaming()
                                } else {
                                    // ... Logic copied from original ...
                                    val hasContent =
                                            messageText.isNotBlank() ||
                                                    chatUiState.selectedImageUri != null ||
                                                    selectedDocumentContent != null
                                    if (hasContent &&
                                                    (chatUiState.remainingRequests > 0 ||
                                                            premiumStatus.isPremium)
                                    ) {
                                        authUiState.user?.uid?.let { userId ->
                                            if (chatUiState.selectedImageUri != null) {
                                                chatViewModel.sendImageMessage(
                                                        userId,
                                                        messageText.ifBlank { "Describe this" },
                                                        chatUiState.selectedImageBase64!!,
                                                        chatUiState.selectedImageUri!!
                                                )
                                            } else if (selectedDocumentContent != null) {
                                                chatViewModel.sendDocumentMessage(
                                                        userId,
                                                        messageText.ifBlank { "Summarize" },
                                                        selectedDocumentName!!,
                                                        selectedDocumentContent!!
                                                )
                                                selectedDocumentName = null
                                                selectedDocumentContent = null
                                            } else {
                                                chatViewModel.sendMessage(
                                                        userId,
                                                        messageText.trim()
                                                )
                                            }
                                            messageText = ""
                                            chatViewModel.setUserTyping(false)
                                        }
                                    }
                                }
                            },
                            isStreaming = chatUiState.isStreaming,
                            isLoading = chatUiState.isLoading,
                            hasImage = chatUiState.selectedImageUri != null,
                            hasDoc = selectedDocumentName != null,
                            onClearImage = { chatViewModel.clearSelectedImage() },
                            onClearDoc = {
                                selectedDocumentName = null
                                selectedDocumentContent = null
                            },
                            imageUri =
                                    chatUiState.selectedImageUri?.let { android.net.Uri.parse(it) },
                            docName = selectedDocumentName,
                            // STT parameters
                            isRecording = isRecording,
                            isTranscribing = false, // Not used with SpeechRecognizer
                            onMicClick = {
                                // Check permission first
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                
                                if (hasPermission) {
                                    speechRecognizerHelper.startListening("id-ID")
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            onStopRecording = {
                                speechRecognizerHelper.stopListening()
                            }
                    )
                }

                // Document Toast (Overlay)
                if (showDocumentToast) {
                    Box(
                            modifier =
                                    Modifier.align(Alignment.TopCenter)
                                            .padding(top = 90.dp) // Below header
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                    ) {
                        Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = Web3Cyan,
                                shadowElevation = 4.dp
                        ) {
                            Row(
                                    modifier =
                                            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                )
                                Text(
                                        text = "📄 $documentToastMessage",
                                        color = Color.Black,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showRateLimitDialog) {
            AlertDialog(
                    onDismissRequest = { showRateLimitDialog = false },
                    icon = {
                        Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFEA4335),
                                modifier = Modifier.size(48.dp)
                        )
                    },
                    title = {
                        Text(
                                text = stringResource(R.string.rate_limit_title),
                                fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column {
                            Text(rateLimitMessage)

                            if (!premiumStatus.isPremium) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                        text = stringResource(R.string.upgrade_premium),
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF4285F4)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showRateLimitDialog = false }) {
                            Text(stringResource(R.string.got_it))
                        }
                    }
            )
        }

        if (showLowQuotaWarning &&
                        chatUiState.remainingRequests in 1..5 &&
                        !chatUiState.isLoadingCounter &&
                        !premiumStatus.isPremium
        ) {
            AlertDialog(
                    onDismissRequest = { showLowQuotaWarning = false },
                    icon = {
                        Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFBBC04),
                                modifier = Modifier.size(40.dp)
                        )
                    },
                    title = { Text(stringResource(R.string.low_quota_title)) },
                    text = {
                        Column {
                            Text(
                                    stringResource(
                                            R.string.low_quota_message,
                                            chatUiState.remainingRequests
                                    )
                            )

                            if (!premiumStatus.isPremium) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                        text = stringResource(R.string.upgrade_premium),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF4285F4)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showLowQuotaWarning = false }) {
                            Text(stringResource(R.string.understood))
                        }
                    }
            )
        }

        // Edit Message Dialog
        if (showEditDialog && editingMessage != null) {
            AlertDialog(
                    onDismissRequest = {
                        showEditDialog = false
                        editingMessage = null
                    },
                    icon = {
                        Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = Color(0xFF4285F4),
                                modifier = Modifier.size(32.dp)
                        )
                    },
                    title = {
                        Text(stringResource(R.string.edit_message), fontWeight = FontWeight.Bold)
                    },
                    text = {
                        OutlinedTextField(
                                value = editingMessageText,
                                onValueChange = { editingMessageText = it },
                                modifier = Modifier.fillMaxWidth().height(150.dp),
                                placeholder = { Text("Edit your message...") },
                                colors =
                                        OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF4285F4),
                                                unfocusedBorderColor =
                                                        if (isDark) Color.White.copy(alpha = 0.3f)
                                                        else Color.Black.copy(alpha = 0.3f)
                                        )
                        )
                    },
                    confirmButton = {
                        Button(
                                onClick = {
                                    authUiState.user?.uid?.let { uid ->
                                        editingMessage?.let { originalMsg ->
                                            if (editingMessageText.isNotBlank()) {
                                                chatViewModel.editAndResendMessage(
                                                        uid,
                                                        originalMsg,
                                                        editingMessageText.trim()
                                                )
                                                showEditDialog = false
                                                editingMessage = null
                                                editingMessageText = ""
                                            }
                                        }
                                    }
                                },
                                enabled = editingMessageText.isNotBlank(),
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF4285F4)
                                        )
                        ) { Text(stringResource(R.string.edit_and_resend)) }
                    },
                    dismissButton = {
                        TextButton(
                                onClick = {
                                    showEditDialog = false
                                    editingMessage = null
                                }
                        ) { Text(stringResource(R.string.cancel)) }
                    }
            )
        }

        // Bottom sheet for image source selection
        if (showImageSourceSheet) {
            ModalBottomSheet(
                    onDismissRequest = { showImageSourceSheet = false },
                    sheetState = sheetState
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Text(
                            text = stringResource(R.string.select_image_source),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Gallery option
                    Surface(
                            onClick = {
                                showImageSourceSheet = false
                                imagePickerLauncher.launch("image/*")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Transparent
                    ) {
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = stringResource(R.string.gallery),
                                    modifier = Modifier.size(28.dp),
                                    tint = Color(0xFF4285F4)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                        text = stringResource(R.string.gallery),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                )
                                Text(
                                        text = stringResource(R.string.choose_from_photos),
                                        style = MaterialTheme.typography.bodySmall,
                                        color =
                                                if (isDark) Color.White.copy(alpha = 0.6f)
                                                else Color.Black.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Camera option
                    Surface(
                            onClick = {
                                showImageSourceSheet = false
                                // Create temp file for camera
                                val photoFile =
                                        File.createTempFile(
                                                "IMG_${System.currentTimeMillis()}_",
                                                ".jpg",
                                                context.cacheDir
                                        )
                                cameraPhotoUri =
                                        FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                photoFile
                                        )
                                cameraLauncher.launch(cameraPhotoUri!!)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Transparent
                    ) {
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = stringResource(R.string.camera),
                                    modifier = Modifier.size(28.dp),
                                    tint = Color(0xFF34A853)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                        text = stringResource(R.string.camera),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                )
                                Text(
                                        text = stringResource(R.string.take_new_photo),
                                        style = MaterialTheme.typography.bodySmall,
                                        color =
                                                if (isDark) Color.White.copy(alpha = 0.6f)
                                                else Color.Black.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Document option
                    Surface(
                            onClick = {
                                showImageSourceSheet = false
                                documentPickerLauncher.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Transparent
                    ) {
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = stringResource(R.string.document),
                                    modifier = Modifier.size(28.dp),
                                    tint = Color(0xFFEA4335)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                        text = stringResource(R.string.document),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                )
                                Text(
                                        text = stringResource(R.string.browse_files),
                                        style = MaterialTheme.typography.bodySmall,
                                        color =
                                                if (isDark) Color.White.copy(alpha = 0.6f)
                                                else Color.Black.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

// Helper function to process image URI
private suspend fun processImageUri(
        context: android.content.Context,
        imageUri: Uri,
        chatViewModel: ChatViewModel,
        snackbarHostState: SnackbarHostState
) {
    try {
        val base64 =
                withContext(Dispatchers.IO) {
                    val inputStream = context.contentResolver.openInputStream(imageUri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    // Resize if too large (max 1024px on largest side)
                    val maxSize = 1024
                    val scale =
                            minOf(
                                    maxSize.toFloat() / bitmap.width,
                                    maxSize.toFloat() / bitmap.height,
                                    1f
                            )
                    val scaledBitmap =
                            if (scale < 1f) {
                                Bitmap.createScaledBitmap(
                                        bitmap,
                                        (bitmap.width * scale).toInt(),
                                        (bitmap.height * scale).toInt(),
                                        true
                                )
                            } else bitmap

                    val outputStream = ByteArrayOutputStream()
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                    Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                }
        chatViewModel.setSelectedImage(imageUri.toString(), base64)
    } catch (e: Exception) {
        snackbarHostState.showSnackbar("Failed to load image")
    }
}

@Composable
private fun FloatingInputBar(
        messageText: String,
        onMessageChange: (String) -> Unit,
        onImagePick: () -> Unit,
        onSend: () -> Unit,
        isStreaming: Boolean,
        isLoading: Boolean,
        hasImage: Boolean,
        hasDoc: Boolean,
        onClearImage: () -> Unit,
        onClearDoc: () -> Unit,
        imageUri: Uri?,
        docName: String?,
        // STT parameters
        isRecording: Boolean = false,
        isTranscribing: Boolean = false,
        onMicClick: () -> Unit = {},
        onStopRecording: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    
    // Pulsing animation for recording
    val pulseScale by animateFloatAsState(
        targetValue = if (isRecording) 1.2f else 1f,
        animationSpec = if (isRecording) {
            infiniteRepeatable(
                animation = tween(500),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            tween(200)
        },
        label = "pulseScale"
    )

    GlassCard(
            backgroundColor = Web3Slate.copy(alpha = 0.9f),
            borderColor = if (isRecording) Color(0xFFEA4335).copy(alpha = 0.5f) else Web3Cyan.copy(alpha = 0.3f),
            shape = RoundedCornerShape(28.dp),
            elevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Previews
            if (hasImage && imageUri != null) {
                Box(modifier = Modifier.padding(bottom = 8.dp).size(80.dp)) {
                    AsyncImage(
                            model = imageUri,
                            contentDescription = "Selected image",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                    )
                    Surface(
                            onClick = onClearImage,
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier.align(Alignment.TopEnd).size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                    Icons.Default.Close,
                                    "Remove",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            if (hasDoc && docName != null) {
                Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Web3Slate,
                        modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                                Icons.Default.Description,
                                null,
                                tint = Color(0xFFEA4335),
                                modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                                text = docName,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 200.dp)
                        )
                        IconButton(onClick = onClearDoc, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            
            // Recording indicator
            if (isRecording) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFEA4335).copy(alpha = 0.2f),
                    modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .scale(pulseScale)
                                .background(Color(0xFFEA4335), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Merekam... Tap mikrofon untuk berhenti",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFEA4335)
                        )
                    }
                }
            }
            
            // Transcribing indicator  
            if (isTranscribing) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Web3Cyan.copy(alpha = 0.2f),
                    modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Web3Cyan
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Mentranskrip audio...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Web3Cyan
                        )
                    }
                }
            }

            // Input Row
            Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onImagePick) {
                    Icon(Icons.Default.AddPhotoAlternate, "Add Image", tint = Web3Cyan)
                }

                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                            value = messageText,
                            onValueChange = onMessageChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                        if (hasImage) stringResource(R.string.describe_image)
                                        else if (isRecording) "Merekam..."
                                        else stringResource(R.string.ask_me_anything),
                                        color = Web3TextSecondary
                                )
                            },
                            shape = RoundedCornerShape(20.dp),
                            maxLines = 4,
                            colors =
                                    OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Web3TextPrimary,
                                            unfocusedTextColor = Web3TextPrimary,
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedBorderColor = Color.Transparent,
                                            unfocusedBorderColor = Color.Transparent,
                                            cursorColor = Web3Cyan
                                    )
                    )
                }
                
                // Microphone Button
                Surface(
                    onClick = { if (isRecording) onStopRecording() else onMicClick() },
                    shape = CircleShape,
                    color = if (isRecording) Color(0xFFEA4335) else Web3Slate,
                    modifier = Modifier.size(48.dp).scale(if (isRecording) pulseScale else 1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isTranscribing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = Web3Cyan
                            )
                        } else {
                            Icon(
                                if (isRecording) Icons.Default.Stop else Icons.Default.Mic, 
                                if (isRecording) "Stop Recording" else "Voice Input",
                                tint = if (isRecording) Color.White else Web3Cyan
                            )
                        }
                    }
                }

                // Send Button
                val canSend =
                        isStreaming ||
                                (messageText.isNotBlank() || hasImage || hasDoc) && !isLoading

                Surface(
                        onClick = onSend,
                        shape = CircleShape,
                        color =
                                if (isStreaming) Color(0xFFEA4335)
                                else if (canSend) Web3Cyan else Web3Slate.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isStreaming) {
                            Icon(Icons.Default.Stop, "Stop", tint = Color.White)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeView(onSuggestionClick: (String) -> Unit) {
    // Randomized prompts
    val allPrompts = stringArrayResource(id = R.array.welcome_prompts)
    val randomPrompts: List<String> = remember { allPrompts.toList().shuffled().take(3) }

    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .padding(horizontal = 32.dp)
                            .padding(bottom = 100.dp), // Avoid overlapping with input
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo / Icon
        Box(
                modifier =
                        Modifier.size(100.dp)
                                .background(
                                        brush =
                                                Brush.linearGradient(
                                                        colors =
                                                                listOf(
                                                                        Web3Cyan.copy(
                                                                                alpha = 0.15f
                                                                        ),
                                                                        Color.Transparent
                                                                )
                                                ),
                                        shape = CircleShape
                                )
                                .border(
                                        width = 1.dp,
                                        brush =
                                                Brush.linearGradient(
                                                        colors =
                                                                listOf(
                                                                        Web3Cyan.copy(alpha = 0.3f),
                                                                        Color.Transparent
                                                                )
                                                ),
                                        shape = CircleShape
                                ),
                contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                    model = R.drawable.logo,
                    contentDescription = "XChatAI Logo",
                    modifier = Modifier.size(64.dp),
                    contentScale = ContentScale.Fit
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Greeting
        Text(
                text = stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Web3TextPrimary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
                text = stringResource(R.string.welcome_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = Web3TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Suggestions
        Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (suggestion in randomPrompts) {
                SuggestionChip(text = suggestion, onClick = { onSuggestionClick(suggestion) })
            }
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    Surface(
            onClick = onClick,
            shape = RoundedCornerShape(20.dp),
            color = Web3Slate.copy(alpha = 0.4f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Web3Cyan.copy(alpha = 0.15f))
    ) {
        Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = text, style = MaterialTheme.typography.bodyMedium, color = Web3TextPrimary)
        }
    }
}
