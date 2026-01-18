package id.xms.xcai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.xms.xcai.ui.theme.Web3Cyan
import id.xms.xcai.ui.theme.Web3Slate

@Composable
fun GlassCard(
        modifier: Modifier = Modifier,
        backgroundColor: Color = Web3Slate.copy(alpha = 0.6f),
        borderColor: Color = Web3Cyan.copy(alpha = 0.2f),
        shape: Shape = RoundedCornerShape(20.dp),
        elevation: Dp = 4.dp,
        content: @Composable () -> Unit
) {
    Surface(
            modifier = modifier,
            shape = shape,
            color = Color.Transparent,
            border = BorderStroke(1.dp, borderColor),
            shadowElevation = elevation
    ) {
        Box(
                modifier =
                        Modifier.background(
                                brush =
                                        Brush.verticalGradient(
                                                colors =
                                                        listOf(
                                                                backgroundColor.copy(alpha = 0.8f),
                                                                backgroundColor.copy(alpha = 0.6f)
                                                        )
                                        )
                        )
        ) {
            // Glass overlay effect
            Box(
                    modifier =
                            Modifier.background(
                                    brush =
                                            Brush.verticalGradient(
                                                    colors =
                                                            listOf(
                                                                    Color.White.copy(alpha = 0.1f),
                                                                    Color.Transparent
                                                            )
                                            )
                            )
            ) { content() }
        }
    }
}

@Composable
fun GlassMessageBubble(
        isUser: Boolean,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
) {
    val backgroundColor =
            if (isUser) {
                Web3Cyan.copy(alpha = 0.1f)
            } else {
                Web3Slate.copy(alpha = 0.6f)
            }

    val borderColor =
            if (isUser) {
                Web3Cyan.copy(alpha = 0.3f)
            } else {
                Web3Cyan.copy(alpha = 0.1f)
            }

    GlassCard(
            modifier = modifier,
            backgroundColor = backgroundColor.copy(alpha = 0.7f),
            borderColor = borderColor,
            shape =
                    RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 20.dp,
                            bottomStart = if (isUser) 20.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 20.dp
                    ),
            elevation = 2.dp,
            content = content
    )
}

// Extension to get color luminance
private fun Color.luminance(): Float {
    return (0.299f * red + 0.587f * green + 0.114f * blue)
}
