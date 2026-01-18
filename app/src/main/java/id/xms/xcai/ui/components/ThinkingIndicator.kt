package id.xms.xcai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xms.xcai.ui.theme.Web3Cyan
import id.xms.xcai.ui.theme.Web3Slate
import id.xms.xcai.ui.theme.Web3TextPrimary
import id.xms.xcai.ui.theme.Web3TextSecondary
import kotlinx.coroutines.delay

/**
 * Animated thinking indicator shown while AI is processing Shows a card with brain icon,
 * "Thinking..." text, and animated dashed line
 */
@Composable
fun AIThinkingIndicator(modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    // Count elapsed time
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            elapsedSeconds++
        }
    }

    // Animated dashed line
    val infiniteTransition = rememberInfiniteTransition(label = "thinking_animation")
    val animatedOffset by
            infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 20f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation = tween(1000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                            ),
                    label = "dash_animation"
            )

    Surface(
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            color = Web3Slate.copy(alpha = 0.6f),
            border =
                    BorderStroke(
                            width = 1.dp,
                            brush =
                                    Brush.linearGradient(
                                            colors =
                                                    listOf(
                                                            Web3Cyan.copy(alpha = 0.3f),
                                                            Web3Cyan.copy(alpha = 0.1f)
                                                    )
                                    )
                    ),
            shadowElevation = 2.dp
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            // Brain icon with gradient background
            Box(
                    modifier =
                            Modifier.size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                            brush =
                                                    Brush.linearGradient(
                                                            colors =
                                                                    listOf(
                                                                            Web3Cyan,
                                                                            Web3Cyan.copy(
                                                                                    alpha = 0.7f
                                                                            )
                                                                    )
                                                    )
                                    ),
                    contentAlignment = Alignment.Center
            ) { Text(text = "🧠", fontSize = 18.sp) }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            text = "Thinking...",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Web3Cyan
                    )

                    // Elapsed time

                    Text(
                            text = formatElapsedTime(elapsedSeconds),
                            style = MaterialTheme.typography.labelSmall,
                            color = Web3TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Animated dashed line
                Box(
                        modifier =
                                Modifier.fillMaxWidth().height(2.dp).drawBehind {
                                    val pathEffect =
                                            PathEffect.dashPathEffect(
                                                    intervals = floatArrayOf(10f, 10f),
                                                    phase = animatedOffset
                                            )
                                    drawLine(
                                            color = Web3Cyan.copy(alpha = 0.5f),
                                            start = Offset(0f, size.height / 2),
                                            end = Offset(size.width, size.height / 2),
                                            strokeWidth = 2f,
                                            pathEffect = pathEffect
                                    )
                                }
                )
            }
        }
    }
}

/**
 * Static thinking card shown after AI finishes thinking Displays the thought process with
 * expand/collapse functionality
 */
@Composable
fun ThinkingCard(thinking: String, modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    var isExpanded by remember { mutableStateOf(false) }

    Surface(
            modifier =
                    modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .animateContentSize(),
            shape = RoundedCornerShape(16.dp),
            color = Web3Slate.copy(alpha = 0.6f),
            border =
                    BorderStroke(
                            width = 1.dp,
                            brush =
                                    Brush.linearGradient(
                                            colors =
                                                    listOf(
                                                            Web3Cyan.copy(alpha = 0.3f),
                                                            Web3Cyan.copy(alpha = 0.1f)
                                                    )
                                    )
                    ),
            shadowElevation = 2.dp
    ) {
        Column(
                modifier =
                        Modifier.fillMaxWidth()
                                .clickable { isExpanded = !isExpanded }
                                .padding(16.dp)
        ) {
            // Header
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    // Brain icon
                    Box(
                            modifier =
                                    Modifier.size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                    brush =
                                                            Brush.linearGradient(
                                                                    colors =
                                                                            listOf(
                                                                                    Web3Cyan,
                                                                                    Web3Cyan.copy(
                                                                                            alpha =
                                                                                                    0.7f
                                                                                    )
                                                                            )
                                                            )
                                            ),
                            contentAlignment = Alignment.Center
                    ) { Text(text = "🧠", fontSize = 18.sp) }

                    Text(
                            text = "Thought Process",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Web3Cyan
                    )
                }

                // Expand/Collapse icon
                Icon(
                        imageVector =
                                if (isExpanded) {
                                    Icons.Default.KeyboardArrowUp
                                } else {
                                    Icons.Default.KeyboardArrowDown
                                },
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = Web3Cyan,
                        modifier = Modifier.size(24.dp)
                )
            }

            // Dashed line separator
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                    modifier =
                            Modifier.fillMaxWidth().height(2.dp).drawBehind {
                                val pathEffect =
                                        PathEffect.dashPathEffect(
                                                intervals = floatArrayOf(10f, 10f),
                                                phase = 0f
                                        )
                                drawLine(
                                        color = Web3Cyan.copy(alpha = 0.5f),
                                        start = Offset(0f, size.height / 2),
                                        end = Offset(size.width, size.height / 2),
                                        strokeWidth = 2f,
                                        pathEffect = pathEffect
                                )
                            }
            )

            // Expandable content
            AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
            ) {
                Text(
                        text = thinking,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                        color = Web3TextPrimary.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = 12.dp)
                )
            }

            // Collapsed preview (first line)
            if (!isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                        text = thinking.lines().firstOrNull()?.take(80) ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Web3TextSecondary,
                        maxLines = 1
                )
            }
        }
    }
}

/** Format elapsed seconds into readable time string */
private fun formatElapsedTime(seconds: Int): String {
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> {
            val mins = seconds / 60
            val secs = seconds % 60
            "${mins}m ${secs}s"
        }
        else -> {
            val hours = seconds / 3600
            val mins = (seconds % 3600) / 60
            "${hours}h ${mins}m"
        }
    }
}

/** Old AITypingIndicator - kept for backward compatibility */
@Composable
fun AITypingIndicator(modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()

    Row(
            modifier = modifier.fillMaxWidth().padding(horizontal = 60.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Start
    ) {
        Surface(
                shape = RoundedCornerShape(18.dp),
                color = Web3Slate.copy(alpha = 0.8f),
                shadowElevation = 1.dp
        ) {
            Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) { repeat(3) { index -> TypingDot(delay = index * 150, isDark = isDark) } }
        }
    }
}

@Composable
private fun TypingDot(delay: Int, isDark: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val scale by
            infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.5f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation =
                                            tween(
                                                    durationMillis = 600,
                                                    delayMillis = delay,
                                                    easing = LinearEasing
                                            ),
                                    repeatMode = RepeatMode.Reverse
                            ),
                    label = "scale"
            )

    Box(modifier = Modifier.size(8.dp * scale).clip(RoundedCornerShape(50)).background(Web3Cyan))
}
