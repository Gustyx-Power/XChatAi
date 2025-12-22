package id.xms.xcai

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xms.xcai.data.repository.UpdateManager
import id.xms.xcai.ui.components.ForceUpdateDialog
import id.xms.xcai.ui.theme.XChatAiTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            XChatAiTheme {
                SplashScreen(
                    onNavigateToMain = {
                        navigateToMain()
                    }
                )
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
        // Add smooth transition
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}

@Composable
private fun SplashScreen(
    onNavigateToMain: () -> Unit
) {
    // ✅ FIX: Get context first INSIDE Composable
    val context = LocalContext.current
    val updateManager = remember { UpdateManager(context) }

    var updateInfo by remember { mutableStateOf<id.xms.xcai.data.repository.AppUpdateInfo?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(true) }
    var showForceUpdate by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // ✨ Logo Scale Animation
    val infiniteTransition = rememberInfiniteTransition(label = "logo_pulse")
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_scale"
    )

    // ✨ Logo Fade In Animation
    val logoAlpha by animateFloatAsState(
        targetValue = if (isCheckingUpdate) 1f else 0f,
        animationSpec = tween(800),
        label = "logo_alpha"
    )

    // ✅ Check for updates
    LaunchedEffect(Unit) {
        delay(1500) // Show splash for minimum 1.5 seconds

        scope.launch {
            updateManager.checkForUpdates()
                .onSuccess { info ->
                    updateInfo = info

                    // Check if force update is required
                    if (updateManager.isUpdateRequired(info)) {
                        showForceUpdate = true
                        isCheckingUpdate = false
                    } else {
                        // No update required, navigate to main
                        delay(500)
                        onNavigateToMain()
                    }
                }
                .onFailure { error ->
                    android.util.Log.e("SplashActivity", "Update check failed: ${error.message}")
                    // Continue to main app even if update check fails
                    delay(500)
                    onNavigateToMain()
                }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D0D0D),
                        Color(0xFF1A1A1A),
                        Color(0xFF0D0D0D)
                    )
                )
            )
    ) {
        // ✨ Animated Logo & Branding
        Column(
            modifier = Modifier
                .fillMaxSize()
                .alpha(logoAlpha),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo with pulse animation
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "XChatAI Logo",
                modifier = Modifier
                    .size(140.dp)
                    .scale(logoScale)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // App Name
            Text(
                text = "XChatAI",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tagline
            Text(
                text = stringResource(R.string.splash_tagline),
                fontSize = 14.sp,
                fontWeight = FontWeight.Light,
                color = Color.White.copy(alpha = 0.7f),
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Loading indicator
            LoadingDots()
        }

        // Version at bottom
        Text(
            text = "v${updateManager.getCurrentVersion()}",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )

        // ✅ Show Force Update Dialog if needed
        if (showForceUpdate && updateInfo != null) {
            ForceUpdateDialog(
                updateInfo = updateInfo!!,
                currentVersion = updateManager.getCurrentVersion()
            )
        }
    }
}

@Composable
private fun LoadingDots() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "dot_$index")

            val dotAlpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 200),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_alpha_$index"
            )

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = Color(0xFF4285F4).copy(alpha = dotAlpha),
                        shape = MaterialTheme.shapes.small
                    )
            )
        }
    }
}
