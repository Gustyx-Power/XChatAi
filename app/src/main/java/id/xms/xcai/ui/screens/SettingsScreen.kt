package id.xms.xcai.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xms.xcai.BuildConfig
import id.xms.xcai.R
import id.xms.xcai.data.model.GroqModel
import id.xms.xcai.data.model.ResponseMode
import id.xms.xcai.ui.viewmodel.SettingsViewModel
import id.xms.xcai.utils.BackupManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val selectedModelId by settingsViewModel.selectedModelId.collectAsState()
    val selectedResponseMode by settingsViewModel.responseMode.collectAsState() // ← NEW

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showRestoreDialog by remember { mutableStateOf(false) }
    var isBackupInProgress by remember { mutableStateOf(false) }
    var isRestoreInProgress by remember { mutableStateOf(false) }

    // Handle system back gesture/button
    BackHandler {
        onNavigateBack()
    }

    // Gradient background matching ChatScreen
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(Color(0xFF1A1A1A), Color(0xFF0D0D0D))
                    } else {
                        listOf(Color(0xFFFAFAFA), Color(0xFFEEEEEE))
                    }
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Surface(
                    color = if (isDark) {
                        Color(0xFF1A1A1A).copy(alpha = 0.95f)
                    } else {
                        Color.White.copy(alpha = 0.95f)
                    },
                    shadowElevation = 4.dp
                ) {
                    TopAppBar(
                        title = {
                            Text(
                                stringResource(R.string.settings),
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else Color.Black
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    stringResource(R.string.back),
                                    tint = if (isDark) Color.White else Color.Black
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Response Mode Section (NEW - PLACED FIRST)
                item {
                    SectionCard(isDark = isDark) {
                        Column {
                            SectionHeader(
                                icon = Icons.Default.FormatListBulleted,
                                text = stringResource(R.string.response_mode),
                                isDark = isDark
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = stringResource(R.string.response_mode_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) {
                                    Color.White.copy(alpha = 0.7f)
                                } else {
                                    Color.Black.copy(alpha = 0.7f)
                                },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            ResponseMode.entries.forEachIndexed { index, mode ->
                                ResponseModeItem(
                                    mode = mode,
                                    isSelected = mode == selectedResponseMode,
                                    isDark = isDark,
                                    onClick = { settingsViewModel.setResponseMode(mode) }
                                )

                                if (index < ResponseMode.entries.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        color = if (isDark) {
                                            Color.White.copy(alpha = 0.1f)
                                        } else {
                                            Color.Black.copy(alpha = 0.1f)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // AI Model Section
                item {
                    SectionCard(isDark = isDark) {
                        Column {
                            SectionHeader(
                                icon = Icons.Default.Psychology,
                                text = stringResource(R.string.ai_model),
                                isDark = isDark
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = stringResource(R.string.ai_model_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) {
                                    Color.White.copy(alpha = 0.7f)
                                } else {
                                    Color.Black.copy(alpha = 0.7f)
                                },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            GroqModel.availableModels.forEachIndexed { index, model ->
                                ModelItem(
                                    model = model,
                                    isSelected = model.id == selectedModelId,
                                    isDark = isDark,
                                    onClick = { settingsViewModel.setSelectedModel(model.id) }
                                )

                                if (index < GroqModel.availableModels.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        color = if (isDark) {
                                            Color.White.copy(alpha = 0.1f)
                                        } else {
                                            Color.Black.copy(alpha = 0.1f)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Data Management Section
                item {
                    SectionCard(isDark = isDark) {
                        Column {
                            SectionHeader(
                                icon = Icons.Default.Storage,
                                text = stringResource(R.string.data_management),
                                isDark = isDark
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = stringResource(R.string.data_management_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) {
                                    Color.White.copy(alpha = 0.7f)
                                } else {
                                    Color.Black.copy(alpha = 0.7f)
                                },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Backup Button
                            DataManagementItem(
                                icon = Icons.Default.Backup,
                                title = stringResource(R.string.backup_now),
                                subtitle = stringResource(R.string.backup_desc),
                                isDark = isDark,
                                isLoading = isBackupInProgress,
                                onClick = {
                                    scope.launch {
                                        isBackupInProgress = true
                                        val backupManager = BackupManager(context)
                                        val result = backupManager.createBackup()
                                        isBackupInProgress = false

                                        result.onSuccess { path ->
                                            snackbarHostState.showSnackbar(context.getString(R.string.backup_success, path))
                                        }.onFailure { error ->
                                            snackbarHostState.showSnackbar(context.getString(R.string.backup_failed, error.message))
                                        }
                                    }
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = if (isDark) {
                                    Color.White.copy(alpha = 0.1f)
                                } else {
                                    Color.Black.copy(alpha = 0.1f)
                                }
                            )

                            // Restore Button
                            DataManagementItem(
                                icon = Icons.Default.RestorePage,
                                title = stringResource(R.string.restore_backup),
                                subtitle = stringResource(R.string.restore_desc),
                                isDark = isDark,
                                isLoading = isRestoreInProgress,
                                onClick = { showRestoreDialog = true }
                            )
                        }
                    }
                }

                // About Section
                item {
                    SectionCard(isDark = isDark) {
                        Column {
                            SectionHeader(
                                icon = Icons.Default.Info,
                                text = stringResource(R.string.about),
                                isDark = isDark
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            InfoItem(
                                label = stringResource(R.string.app_version),
                                value = BuildConfig.VERSION_NAME,
                                isDark = isDark
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            InfoItem(
                                label = stringResource(R.string.developer),
                                value = stringResource(R.string.developer_name),
                                isDark = isDark
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // Restore Confirmation Dialog
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.RestorePage,
                    contentDescription = null,
                    tint = Color(0xFF4285F4),
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    stringResource(R.string.restore_backup),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(stringResource(R.string.restore_confirm))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreDialog = false
                        scope.launch {
                            isRestoreInProgress = true
                            val backupManager = BackupManager(context)
                            val result = backupManager.restoreBackup()
                            isRestoreInProgress = false

                            result.onSuccess {
                                snackbarHostState.showSnackbar(context.getString(R.string.restore_success))
                            }.onFailure { error ->
                                snackbarHostState.showSnackbar(context.getString(R.string.restore_failed, error.message))
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4285F4)
                    )
                ) {
                    Text(stringResource(R.string.restore))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun SectionCard(
    isDark: Boolean,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) {
            Color(0xFF2D2D2D).copy(alpha = 0.7f)
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
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    text: String,
    isDark: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF4285F4).copy(alpha = 0.2f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF4285F4),
                modifier = Modifier.padding(8.dp).size(20.dp)
            )
        }

        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isDark) Color.White else Color.Black
        )
    }
}

// NEW: Response Mode Item Composable
@Composable
private fun ResponseModeItem(
    mode: ResponseMode,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            Color(0xFF4285F4).copy(alpha = 0.15f)
        } else {
            Color.Transparent
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Emoji icon
            Text(
                text = mode.icon,
                fontSize = 28.sp,
                modifier = Modifier.size(40.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mode.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isDark) Color.White else Color.Black
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = mode.description,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = if (isDark) {
                        Color.White.copy(alpha = 0.7f)
                    } else {
                        Color.Black.copy(alpha = 0.7f)
                    }
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF4285F4),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun ModelItem(
    model: GroqModel,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            Color(0xFF4285F4).copy(alpha = 0.15f)
        } else {
            Color.Transparent
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = Color(0xFF4285F4)
                )
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isDark) Color.White else Color.Black
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${model.developer} • ${model.contextWindow / 1024}K context",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) {
                        Color.White.copy(alpha = 0.7f)
                    } else {
                        Color.Black.copy(alpha = 0.7f)
                    }
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = model.description,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = if (isDark) {
                        Color.White.copy(alpha = 0.6f)
                    } else {
                        Color.Black.copy(alpha = 0.6f)
                    }
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF4285F4),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun DataManagementItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isDark: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = !isLoading,
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF4285F4).copy(alpha = 0.2f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF4285F4),
                    modifier = Modifier.padding(10.dp).size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isDark) Color.White else Color.Black
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) {
                        Color.White.copy(alpha = 0.7f)
                    } else {
                        Color.Black.copy(alpha = 0.7f)
                    }
                )
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF4285F4)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = if (isDark) {
                        Color.White.copy(alpha = 0.6f)
                    } else {
                        Color.Black.copy(alpha = 0.6f)
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun InfoItem(
    label: String,
    value: String,
    isDark: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDark) {
                Color.White.copy(alpha = 0.7f)
            } else {
                Color.Black.copy(alpha = 0.7f)
            }
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (isDark) Color.White else Color.Black
        )
    }
}
