package id.xms.xcai.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.pager.*
import id.xms.xcai.R
import kotlinx.coroutines.launch

data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val isDeveloperPage: Boolean = false // ← NEW FLAG
)

@OptIn(ExperimentalPagerApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current

    val pages = listOf(
        OnboardingPage(
            title = stringResource(R.string.onboarding_welcome_title),
            description = stringResource(R.string.onboarding_welcome_desc),
            icon = Icons.Default.EmojiEmotions,
            color = Color(0xFF4285F4)
        ),
        OnboardingPage(
            title = stringResource(R.string.onboarding_models_title),
            description = stringResource(R.string.onboarding_models_desc),
            icon = Icons.Default.Psychology,
            color = Color(0xFF34A853)
        ),
        OnboardingPage(
            title = stringResource(R.string.onboarding_modes_title),
            description = stringResource(R.string.onboarding_modes_desc),
            icon = Icons.Default.FormatListBulleted,
            color = Color(0xFFFBBC04)
        ),
        OnboardingPage(
            title = stringResource(R.string.onboarding_developer_title),
            description = stringResource(R.string.onboarding_developer_desc),
            icon = Icons.Default.Code,
            color = Color(0xFFEA4335),
            isDeveloperPage = true // ← SPECIAL FLAG FOR DEVELOPER PAGE
        )
    )

    val pagerState = rememberPagerState()
    val scope = rememberCoroutineScope()

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
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Pager
            HorizontalPager(
                count = pages.size,
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                if (pages[page].isDeveloperPage) {
                    // Special layout for developer page
                    DeveloperPageContent(
                        page = pages[page],
                        isDark = isDark,
                        onVisitClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://gustyx-power.github.io/My-Portofolio/"))
                            context.startActivity(intent)
                        }
                    )
                } else {
                    // Normal layout
                    OnboardingPageContent(
                        page = pages[page],
                        isDark = isDark
                    )
                }
            }

            // Bottom section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Page indicator
                HorizontalPagerIndicator(
                    pagerState = pagerState,
                    modifier = Modifier.padding(16.dp),
                    activeColor = Color(0xFF4285F4),
                    inactiveColor = if (isDark) {
                        Color.White.copy(alpha = 0.3f)
                    } else {
                        Color.Black.copy(alpha = 0.3f)
                    },
                    indicatorWidth = 8.dp,
                    indicatorHeight = 8.dp,
                    spacing = 8.dp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End, // Changed from SpaceBetween to End
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    // Next/Get Started button
                    Button(
                        onClick = {
                            if (pagerState.currentPage < pages.size - 1) {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            } else {
                                onComplete()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4285F4)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .height(56.dp)
                        // No need for weight if it's the only item and we use Arrangement.End
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (pagerState.currentPage < pages.size - 1) stringResource(R.string.next) else stringResource(R.string.get_started),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = if (pagerState.currentPage < pages.size - 1) {
                                    Icons.Default.ArrowForward
                                } else {
                                    Icons.Default.Check
                                },
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    isDark: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon with colored background
        Surface(
            shape = CircleShape,
            color = page.color.copy(alpha = 0.2f),
            modifier = Modifier.size(120.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    tint = page.color,
                    modifier = Modifier.size(56.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Title
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (isDark) Color.White else Color.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Description
        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isDark) {
                Color.White.copy(alpha = 0.8f)
            } else {
                Color.Black.copy(alpha = 0.8f)
            },
            textAlign = TextAlign.Center,
            lineHeight = 28.sp
        )
    }
}

// NEW: Special layout for Developer page
@Composable
private fun DeveloperPageContent(
    page: OnboardingPage,
    isDark: Boolean,
    onVisitClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Profile Photo with border
        Surface(
            shape = CircleShape,
            color = page.color.copy(alpha = 0.2f),
            border = BorderStroke(4.dp, page.color.copy(alpha = 0.5f)),
            modifier = Modifier.size(140.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.profile),
                contentDescription = "Developer Profile",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Developer Name with icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Code,
                contentDescription = null,
                tint = page.color,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = "Gusti Aditya Muzaky",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color.Black,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Role badge
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = page.color.copy(alpha = 0.2f)
        ) {
            Text(
                text = stringResource(R.string.developer_role),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = page.color,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Description
        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isDark) {
                Color.White.copy(alpha = 0.8f)
            } else {
                Color.Black.copy(alpha = 0.8f)
            },
            textAlign = TextAlign.Center,
            lineHeight = 28.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Visit Portfolio Button
        Button(
            onClick = onVisitClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = page.color
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(R.string.visit_portfolio),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Social links hint
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = stringResource(R.string.portfolio_info),
                style = MaterialTheme.typography.bodySmall,
                color = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }
}
