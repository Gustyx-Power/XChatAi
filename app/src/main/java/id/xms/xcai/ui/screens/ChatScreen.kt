package id.xms.xcai.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import id.xms.xcai.R
import id.xms.xcai.ui.components.AIThinkingIndicator
import id.xms.xcai.ui.components.AITypingIndicator
import id.xms.xcai.ui.components.MessageItem
import id.xms.xcai.ui.components.StreamingMessageItem
import id.xms.xcai.ui.viewmodel.AuthViewModel
import id.xms.xcai.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

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

    val context = LocalContext.current

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { imageUri ->
            scope.launch {
                processImageUri(context, imageUri, chatViewModel, snackbarHostState)
            }
        }
    }

    // Camera photo URI
    var cameraPhotoUri by remember { mutableStateOf<Uri?>(null) }
    
    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraPhotoUri != null) {
            scope.launch {
                processImageUri(context, cameraPhotoUri!!, chatViewModel, snackbarHostState)
            }
        }
    }

    // Bottom sheet state for image source picker
    var showImageSourceSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(messageText) {
        chatViewModel.setUserTyping(messageText.isNotEmpty())
    }

    DisposableEffect(Unit) {
        onDispose {
            chatViewModel.setUserTyping(false)
        }
    }

    LaunchedEffect(chatUiState.remainingRequests, chatUiState.isLoadingCounter) {
        if (!chatUiState.isLoadingCounter && chatUiState.remainingRequests == 0 && !premiumStatus.isPremium) {
            rateLimitMessage = "You've reached the maximum of 20 requests per 30 minutes. Please wait before sending more messages."
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
                error.contains("wait", ignoreCase = true)) {

                rateLimitMessage = if (premiumStatus.isPremium) {
                    "You've reached your ${premiumStatus.maxRequests} requests limit. Please wait 30 minutes before sending more messages."
                } else {
                    error
                }
                showRateLimitDialog = true

            } else {
                snackbarHostState.showSnackbar(
                    message = error,
                    duration = SnackbarDuration.Short
                )
            }

            chatViewModel.clearError()
        }
    }

    LaunchedEffect(chatUiState.remainingRequests) {
        if (chatUiState.remainingRequests in 1..5 && !showLowQuotaWarning && !chatUiState.isLoadingCounter && !premiumStatus.isPremium) {
            showLowQuotaWarning = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(
                            Color(0xFF1A1A1A),
                            Color(0xFF0D0D0D)
                        )
                    } else {
                        listOf(
                            Color(0xFFFAFAFA),
                            Color(0xFFEEEEEE)
                        )
                    }
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "XChatAi",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = if (isDark) Color.White else Color.Black
                                )

                                if (premiumStatus.isPremium) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = when (premiumStatus.tier) {
                                            "premium_plus" -> Color(0xFFFFD700)
                                            else -> Color(0xFF4285F4).copy(alpha = 0.2f)
                                        }
                                    ) {
                                        Text(
                                            text = when (premiumStatus.tier) {
                                                "premium_plus" -> "💎 PLUS"
                                                else -> "⭐ PRO"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = when (premiumStatus.tier) {
                                                "premium_plus" -> Color(0xFF1A1A1A)
                                                else -> Color(0xFF4285F4)
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (chatUiState.remainingRequests <= 5 && !chatUiState.isLoadingCounter && !premiumStatus.isPremium) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = Color(0xFFEA4335)
                                    )
                                }

                                if (chatUiState.isLoadingCounter) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.dp,
                                        color = Color(0xFF4285F4)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Syncing...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isDark) {
                                            Color.White.copy(alpha = 0.6f)
                                        } else {
                                            Color.Black.copy(alpha = 0.6f)
                                        }
                                    )
                                } else {
                                    val displayText = if (premiumStatus.maxRequests == -1) {
                                        "✨ Unlimited requests"
                                    } else {
                                        "${chatUiState.remainingRequests}/${premiumStatus.maxRequests} requests"
                                    }

                                    Text(
                                        text = displayText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when {
                                            premiumStatus.isPremium -> Color(0xFF4285F4)
                                            chatUiState.remainingRequests <= 5 -> Color(0xFFEA4335)
                                            else -> if (isDark) {
                                                Color.White.copy(alpha = 0.6f)
                                            } else {
                                                Color.Black.copy(alpha = 0.6f)
                                            }
                                        },
                                        fontWeight = if (premiumStatus.isPremium) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(
                                Icons.Default.Menu,
                                "Menu",
                                tint = if (isDark) Color.White else Color.Black
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isDark) {
                            Color(0xFF1A1A1A).copy(alpha = 0.95f)
                        } else {
                            Color.White.copy(alpha = 0.95f)
                        },
                        titleContentColor = if (isDark) Color.White else Color.Black
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                if (chatUiState.remainingRequests < 20 && !chatUiState.isLoadingCounter && !premiumStatus.isPremium) {
                    val maxReq = if (premiumStatus.maxRequests == -1) Int.MAX_VALUE else premiumStatus.maxRequests
                    val progress = chatUiState.remainingRequests.toFloat() / maxReq.toFloat()
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = when {
                            chatUiState.remainingRequests <= 5 -> Color(0xFFEA4335)
                            chatUiState.remainingRequests <= 10 -> Color(0xFFFBBC04)
                            else -> Color(0xFF4285F4)
                        },
                        trackColor = if (isDark) {
                            Color.White.copy(alpha = 0.1f)
                        } else {
                            Color.Black.copy(alpha = 0.1f)
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (chatUiState.messages.isEmpty() && !chatUiState.isLoading) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Hello, ${authUiState.user?.displayName?.split(" ")?.firstOrNull() ?: "User"}",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Normal,
                                color = if (isDark) Color(0xFF8AB4F8) else Color(0xFF1A73E8),
                                fontSize = 32.sp
                            )

                            if (premiumStatus.isPremium) {
                                Spacer(modifier = Modifier.size(12.dp))
                                Text(
                                    text = "✨ You have premium access!",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color(0xFFFFD700)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(
                                items = chatUiState.messages,
                                key = { it.id }
                            ) { message ->
                                // ✅ Determine if this is the last user message
                                val messageIndex = chatUiState.messages.indexOf(message)
                                val isLastUserMessage = message.isUser &&
                                        messageIndex == chatUiState.messages.indexOfLast { it.isUser }

                                MessageItem(
                                    message = message
                                )
                            }

                            if (chatUiState.isStreaming && chatUiState.streamingText.isNotEmpty()) {
                                item(key = "streaming_message") {
                                    StreamingMessageItem(
                                        text = chatUiState.streamingText
                                    )
                                }
                            }

                            if (chatUiState.isThinking) {
                                item(key = "thinking_indicator") {
                                    AIThinkingIndicator()
                                }
                            } else if (chatUiState.isLoading && !chatUiState.isStreaming && chatUiState.messages.isNotEmpty()) {
                                item(key = "typing_indicator") {
                                    AITypingIndicator()
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .padding(16.dp)
                ) {
                    if (chatUiState.remainingRequests <= 5 && chatUiState.remainingRequests > 0 && !chatUiState.isLoadingCounter && !premiumStatus.isPremium) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFEA4335),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "Only ${chatUiState.remainingRequests} requests left",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFEA4335)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Attachment button
                        Surface(
                            onClick = {
                                showImageSourceSheet = true
                            },
                            shape = RoundedCornerShape(28.dp),
                            color = if (isDark) {
                                Color(0xFF2D2D2D).copy(alpha = 0.8f)
                            } else {
                                Color.White.copy(alpha = 0.9f)
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isDark) {
                                    Color.White.copy(alpha = 0.1f)
                                } else {
                                    Color.Black.copy(alpha = 0.1f)
                                }
                            ),
                            enabled = !chatUiState.isLoading && (chatUiState.remainingRequests > 0 || premiumStatus.isPremium),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = "Add Image",
                                    tint = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.6f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Input field with image preview
                        Column(modifier = Modifier.weight(1f)) {
                            // Image preview if selected
                            chatUiState.selectedImageUri?.let { imageUri ->
                                Box(
                                    modifier = Modifier
                                        .padding(bottom = 8.dp)
                                        .size(80.dp)
                                ) {
                                    AsyncImage(
                                        model = imageUri,
                                        contentDescription = "Selected image",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    // Remove button
                                    Surface(
                                        onClick = { chatViewModel.clearSelectedImage() },
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.6f),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(24.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(28.dp),
                                color = if (isDark) {
                                    Color(0xFF2D2D2D).copy(alpha = 0.8f)
                                } else {
                                    Color.White.copy(alpha = 0.9f)
                                },
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isDark) {
                                        Color.White.copy(alpha = 0.1f)
                                    } else {
                                        Color.Black.copy(alpha = 0.1f)
                                    }
                                ),
                                shadowElevation = 4.dp
                            ) {
                                OutlinedTextField(
                                    value = messageText,
                                    onValueChange = {
                                        messageText = it
                                        chatViewModel.setUserTyping(it.isNotEmpty())
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = {
                                        Text(
                                            if (chatUiState.selectedImageUri != null) stringResource(R.string.describe_image) else stringResource(R.string.ask_me_anything),
                                            color = if (isDark) {
                                                Color.White.copy(alpha = 0.5f)
                                            } else {
                                                Color.Black.copy(alpha = 0.5f)
                                            }
                                        )
                                    },
                                    shape = RoundedCornerShape(28.dp),
                                    maxLines = 4,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = if (isDark) Color.White else Color.Black,
                                        unfocusedTextColor = if (isDark) Color.White else Color.Black,
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        cursorColor = Color(0xFF4285F4)
                                    ),
                                    enabled = !chatUiState.isLoading && (chatUiState.remainingRequests > 0 || premiumStatus.isPremium) && !chatUiState.isLoadingCounter
                                )
                            }
                        }

                        // Send button
                        val hasContent = messageText.isNotBlank() || chatUiState.selectedImageUri != null
                        Surface(
                            onClick = {
                                if (chatUiState.isStreaming) {
                                    chatViewModel.stopStreaming()
                                } else {
                                    if (hasContent && (chatUiState.remainingRequests > 0 || premiumStatus.isPremium)) {
                                        authUiState.user?.uid?.let { userId ->
                                            // Check if we have an image
                                            val imageBase64 = chatUiState.selectedImageBase64
                                            val imageUri = chatUiState.selectedImageUri
                                            
                                            if (imageBase64 != null && imageUri != null) {
                                                // Send as vision message
                                                val prompt = if (messageText.isBlank()) "What's in this image?" else messageText.trim()
                                                chatViewModel.sendImageMessage(userId, prompt, imageBase64, imageUri)
                                                messageText = ""
                                            } else if (messageText.isNotBlank()) {
                                                // Regular text message
                                                chatViewModel.sendMessage(userId, messageText.trim())
                                                messageText = ""
                                            }
                                            chatViewModel.setUserTyping(false)
                                        }
                                    } else if (chatUiState.remainingRequests == 0 && !premiumStatus.isPremium) {
                                        showRateLimitDialog = true
                                        rateLimitMessage = "You've reached the maximum of 20 requests per 30 minutes."
                                    }
                                }
                            },
                            shape = RoundedCornerShape(28.dp),
                            color = if (chatUiState.isStreaming) {
                                Color(0xFFEA4335)
                            } else if (hasContent && !chatUiState.isLoading && (chatUiState.remainingRequests > 0 || premiumStatus.isPremium) && !chatUiState.isLoadingCounter) {
                                Color(0xFF4285F4)
                            } else {
                                if (isDark) {
                                    Color(0xFF2D2D2D).copy(alpha = 0.5f)
                                } else {
                                    Color(0xFFCCCCCC).copy(alpha = 0.5f)
                                }
                            },
                            enabled = chatUiState.isStreaming || (hasContent && !chatUiState.isLoading && (chatUiState.remainingRequests > 0 || premiumStatus.isPremium) && !chatUiState.isLoadingCounter),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (chatUiState.isStreaming) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = "Stop",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send",
                                        tint = if (hasContent && !chatUiState.isLoading) {
                                            Color.White
                                        } else {
                                            Color.White.copy(alpha = 0.3f)
                                        }
                                    )
                                }
                            }
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

    if (showLowQuotaWarning && chatUiState.remainingRequests in 1..5 && !chatUiState.isLoadingCounter && !premiumStatus.isPremium) {
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
            title = {
                Text(stringResource(R.string.low_quota_title))
            },
            text = {
                Column {
                    Text(stringResource(R.string.low_quota_message, chatUiState.remainingRequests))

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

    // Bottom sheet for image source selection
    if (showImageSourceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showImageSourceSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
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
                                color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
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
                        val photoFile = File.createTempFile(
                            "IMG_${System.currentTimeMillis()}_",
                            ".jpg",
                            context.cacheDir
                        )
                        cameraPhotoUri = FileProvider.getUriForFile(
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
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
                                color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
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
        val base64 = withContext(Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            // Resize if too large (max 1024px on largest side)
            val maxSize = 1024
            val scale = minOf(
                maxSize.toFloat() / bitmap.width,
                maxSize.toFloat() / bitmap.height,
                1f
            )
            val scaledBitmap = if (scale < 1f) {
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
