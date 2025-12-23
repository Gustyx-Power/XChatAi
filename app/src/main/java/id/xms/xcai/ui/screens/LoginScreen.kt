package id.xms.xcai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xms.xcai.R
import id.xms.xcai.ui.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val authUiState by authViewModel.authUiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isDark = isSystemInDarkTheme()

    // Handle navigation on successful login
    LaunchedEffect(authUiState.user) {
        if (authUiState.user != null) {
            onLoginSuccess()
        }
    }

    // Show error snackbar
    LaunchedEffect(authUiState.error) {
        authUiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            authViewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { paddingValues ->
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
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                // App Logo with gradient background
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF4285F4).copy(alpha = 0.2f),
                    border = BorderStroke(2.dp, Color(0xFF4285F4).copy(alpha = 0.3f)),
                    modifier = Modifier.size(140.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = "App Logo",
                            modifier = Modifier.size(80.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // App Name
                Text(
                    text = "XChatAi",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4285F4)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Tagline
                Text(
                    text = stringResource(R.string.login_tagline),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDark) {
                        Color.White.copy(alpha = 0.8f)
                    } else {
                        Color.Black.copy(alpha = 0.8f)
                    },
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(48.dp))

                // Features preview
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isDark) {
                        Color(0xFF2D2D2D).copy(alpha = 0.7f)
                    } else {
                        Color.White.copy(alpha = 0.9f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FeatureItem("⚡", stringResource(R.string.feature_instant_responses), isDark)
                        FeatureItem("🧠", stringResource(R.string.feature_multiple_models), isDark)
                        FeatureItem("💻", stringResource(R.string.feature_code_generation), isDark)
                        FeatureItem("🎯", stringResource(R.string.feature_custom_modes), isDark)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Sign in button
                if (authUiState.isLoading) {
                    CircularProgressIndicator(
                        color = Color(0xFF4285F4),
                        modifier = Modifier.size(48.dp)
                    )
                } else {
                    Button(
                        onClick = {
                            val webClientId = "1023109162505-jbbf9sdkb7njiunc459oc8fl3ffoc14u.apps.googleusercontent.com"
                            authViewModel.signInWithGoogle(webClientId)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4285F4)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Login,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                stringResource(R.string.sign_in_google),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Footer text
                Text(
                    text = stringResource(R.string.terms_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) {
                        Color.White.copy(alpha = 0.5f)
                    } else {
                        Color.Black.copy(alpha = 0.5f)
                    },
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun FeatureItem(emoji: String, text: String, isDark: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = emoji, fontSize = 20.sp)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDark) Color.White else Color.Black
        )
    }
}
