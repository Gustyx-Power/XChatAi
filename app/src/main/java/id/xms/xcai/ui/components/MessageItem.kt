package id.xms.xcai.ui.components

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xms.xcai.data.local.ChatEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Data classes
data class ParsedMessage(
    val thinking: String?,
    val content: List<MessageContent>
)

sealed class MessageContent {
    data class Text(val text: String) : MessageContent()
    data class CodeBlock(val code: String, val language: String) : MessageContent()
    data class Heading(val text: String, val level: Int) : MessageContent()
    data class BulletList(val items: List<String>) : MessageContent()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MessageContent()
}

// Parsing functions
@SuppressLint("SuspiciousIndentation")
fun parseMessageContent(message: String): ParsedMessage {
    val thinkPattern = """<think>(.*?)</think>""".toRegex(RegexOption.DOT_MATCHES_ALL)
    val thinkMatch = thinkPattern.find(message)
    val thinking = thinkMatch?.groupValues?.getOrNull(1)?.trim()

    val cleanMessage = if (thinking != null) {
        message.replace(thinkPattern, "").trim()
    } else {
        message
    }

    val content = mutableListOf<MessageContent>()
    val lines = cleanMessage.lines()
    var i = 0
    val textBuffer = mutableListOf<String>()
    var inCodeBlock = false
    var codeLanguage = ""
    val codeBuffer = mutableListOf<String>()

    while (i < lines.size) {
        val line = lines[i]
        val trimmedLine = line.trim()

        when {
            trimmedLine.startsWith("```") -> {
                if (!inCodeBlock) {
                    if (textBuffer.isNotEmpty()) {
                        content.addAll(parseTextWithMarkdown(textBuffer.joinToString("\n")))
                        textBuffer.clear()
                    }
                    inCodeBlock = true
                    codeLanguage = trimmedLine.removePrefix("```").trim()
                    if (codeLanguage.isEmpty()) {
                        codeLanguage = "plaintext"
                    }
                } else {
                    inCodeBlock = false
                    val code = codeBuffer.joinToString("\n").trim()
                    if (code.isNotEmpty()) {
                        content.add(MessageContent.CodeBlock(code, codeLanguage))
                    }
                    codeBuffer.clear()
                    codeLanguage = ""
                }
        }
        else -> {
            if (inCodeBlock) {
                codeBuffer.add(line)
            } else {
                textBuffer.add(line)
            }
        }
    }
    i++
}

if (textBuffer.isNotEmpty()) {
    content.addAll(parseTextWithMarkdown(textBuffer.joinToString("\n")))
}

if (inCodeBlock && codeBuffer.isNotEmpty()) {
    content.add(MessageContent.CodeBlock(codeBuffer.joinToString("\n").trim(), codeLanguage))
}

if (content.isEmpty()) {
    content.add(MessageContent.Text(cleanMessage))
}

return ParsedMessage(thinking, content)
}

fun parseMarkdownTable(lines: List<String>, startIndex: Int): Pair<MessageContent.Table?, Int> {
    if (startIndex >= lines.size) return null to startIndex

    val line = lines[startIndex].trim()

    // Must contain pipes and have at least 2 columns
    if (!line.startsWith("|") || line.count { it == '|' } < 3) {
        return null to startIndex
    }

    // Parse header
    val headers = line
        .split("|")
        .drop(1)  // Remove first empty element from leading |
        .dropLast(1)  // Remove last empty element from trailing |
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    if (headers.isEmpty()) return null to startIndex

    android.util.Log.d("TableParser", "Headers: $headers")

    var currentIndex = startIndex + 1

    // Skip separator line (|---|---|) or (|---------|----------|)
    if (currentIndex < lines.size) {
        val separatorLine = lines[currentIndex].trim()
        // More flexible separator pattern
        if (separatorLine.startsWith("|") && separatorLine.contains("-")) {
            android.util.Log.d("TableParser", "Found separator: $separatorLine")
            currentIndex++ // Skip separator
        }
    }

    // Parse rows
    val rows = mutableListOf<List<String>>()
    while (currentIndex < lines.size) {
        val rowLine = lines[currentIndex].trim()

        // Stop if not a table row (must start with |)
        if (!rowLine.startsWith("|")) {
            android.util.Log.d("TableParser", "Row doesn't start with |, stopping")
            break
        }

        // Stop if it looks like a separator
        if (rowLine.contains("---") || rowLine.matches("""^\|[\s\-:]+\|$""".toRegex())) {
            android.util.Log.d("TableParser", "Found separator line, skipping")
            currentIndex++
            continue
        }

        val cells = rowLine
            .split("|")
            .drop(1)  // Remove first empty
            .dropLast(1)  // Remove last empty
            .map { it.trim() }

        android.util.Log.d("TableParser", "Row cells: $cells (expected ${headers.size})")

        // Accept row if it has same or similar number of columns
        if (cells.size == headers.size) {
            rows.add(cells)
        } else if (cells.size > 0) {
            // Pad or truncate to match headers
            val adjustedCells = cells.take(headers.size).toMutableList()
            while (adjustedCells.size < headers.size) {
                adjustedCells.add("")
            }
            rows.add(adjustedCells)
            android.util.Log.d("TableParser", "Adjusted row: $adjustedCells")
        }

        currentIndex++
    }

    android.util.Log.d("TableParser", "Total rows: ${rows.size}")

    return if (rows.isNotEmpty()) {
        MessageContent.Table(headers, rows) to currentIndex
    } else {
        null to startIndex
    }
}


fun parseTextWithMarkdown(text: String): List<MessageContent> {
    val result = mutableListOf<MessageContent>()
    val lines = text.lines()
    var i = 0
    val textBuffer = mutableListOf<String>()
    val listBuffer = mutableListOf<String>()

    while (i < lines.size) {
        val line = lines[i]
        val trimmedLine = line.trim()

        when {
            // Check for table
            trimmedLine.contains("|") && trimmedLine.count { it == '|' } >= 2 -> {
                // Flush buffers
                if (textBuffer.isNotEmpty()) {
                    val bufferedText = textBuffer.joinToString("\n").trim()
                    if (bufferedText.isNotEmpty()) {
                        result.addAll(parseInlineFormatting(bufferedText))
                    }
                    textBuffer.clear()
                }
                if (listBuffer.isNotEmpty()) {
                    result.add(MessageContent.BulletList(listBuffer.toList()))
                    listBuffer.clear()
                }

                // Try to parse table
                val (table, newIndex) = parseMarkdownTable(lines, i)
                if (table != null) {
                    result.add(table)
                    i = newIndex
                    continue
                } else {
                    textBuffer.add(line)
                }
            }
            trimmedLine.matches("""^#{1,3}\s+.+$""".toRegex()) -> {
                if (textBuffer.isNotEmpty()) {
                    val bufferedText = textBuffer.joinToString("\n").trim()
                    if (bufferedText.isNotEmpty()) {
                        result.addAll(parseInlineFormatting(bufferedText))
                    }
                    textBuffer.clear()
                }
                if (listBuffer.isNotEmpty()) {
                    result.add(MessageContent.BulletList(listBuffer.toList()))
                    listBuffer.clear()
                }
                val level = trimmedLine.takeWhile { it == '#' }.length
                val headingText = trimmedLine.dropWhile { it == '#' }.trim()
                result.add(MessageContent.Heading(headingText, level))
            }
            trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ") -> {
                if (textBuffer.isNotEmpty()) {
                    val bufferedText = textBuffer.joinToString("\n").trim()
                    if (bufferedText.isNotEmpty()) {
                        result.addAll(parseInlineFormatting(bufferedText))
                    }
                    textBuffer.clear()
                }
                val itemText = trimmedLine.removePrefix("- ").removePrefix("* ")
                listBuffer.add(itemText)
            }
            trimmedLine.matches("""^\d+\.\s+.+""".toRegex()) -> {
                if (textBuffer.isNotEmpty()) {
                    val bufferedText = textBuffer.joinToString("\n").trim()
                    if (bufferedText.isNotEmpty()) {
                        result.addAll(parseInlineFormatting(bufferedText))
                    }
                    textBuffer.clear()
                }
                val itemText = trimmedLine.replaceFirst("""^\d+\.\s+""".toRegex(), "")
                listBuffer.add(itemText)
            }
            trimmedLine.isEmpty() -> {
                if (listBuffer.isNotEmpty()) {
                    result.add(MessageContent.BulletList(listBuffer.toList()))
                    listBuffer.clear()
                }
                if (textBuffer.isNotEmpty() && textBuffer.last().isNotEmpty()) {
                    textBuffer.add("")
                }
            }
            else -> {
                if (listBuffer.isNotEmpty()) {
                    result.add(MessageContent.BulletList(listBuffer.toList()))
                    listBuffer.clear()
                }
                textBuffer.add(line)
            }
        }
        i++
    }

    if (listBuffer.isNotEmpty()) {
        result.add(MessageContent.BulletList(listBuffer.toList()))
    }

    if (textBuffer.isNotEmpty()) {
        val bufferedText = textBuffer.joinToString("\n").trim()
        if (bufferedText.isNotEmpty()) {
            result.addAll(parseInlineFormatting(bufferedText))
        }
    }

    return result
}

fun parseInlineFormatting(text: String): List<MessageContent> {
    val result = mutableListOf<MessageContent>()
    val inlineCodePattern = """`([^`\n]+)`""".toRegex()
    var lastIndex = 0

    val matches = inlineCodePattern.findAll(text).toList()

    matches.forEach { match ->
        if (match.range.first > lastIndex) {
            val textBefore = text.substring(lastIndex, match.range.first)
            if (textBefore.isNotEmpty()) {
                result.add(MessageContent.Text(textBefore))
            }
        }
        result.add(MessageContent.CodeBlock(match.groupValues[1], "inline"))
        lastIndex = match.range.last + 1
    }

    if (lastIndex < text.length) {
        val remaining = text.substring(lastIndex)
        if (remaining.isNotEmpty()) {
            result.add(MessageContent.Text(remaining))
        }
    }

    if (result.isEmpty() && text.isNotEmpty()) {
        result.add(MessageContent.Text(text))
    }

    return result
}

// Composables with Theme Support
@Composable
fun MessageItem(
    message: ChatEntity,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    streamingText: String = ""
) {
    val isDark = isSystemInDarkTheme()
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeString = timeFormat.format(Date(message.timestamp))

    if (message.isUser) {
        UserMessageBubble(
            message = message.message,
            time = timeString,
            isDark = isDark,
            modifier = modifier
        )
    } else {
        val messageToShow = if (isStreaming) streamingText else message.message
        val parsed = remember(messageToShow) {
            parseMessageContent(messageToShow)
        }
        AIMessageWithContent(
            thinking = parsed.thinking,
            content = parsed.content,
            time = timeString,
            isDark = isDark,
            modifier = modifier,
            isStreaming = isStreaming,
            fullMessageText = message.message
        )
    }
}

@Composable
private fun UserMessageBubble(
    message: String,
    time: String,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
                color = Color.Transparent,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isDark) {
                        Color(0xFF4285F4).copy(alpha = 0.3f)
                    } else {
                        Color(0xFF1A73E8).copy(alpha = 0.4f)
                    }
                ),
                shadowElevation = 2.dp
            ) {
                Box(
                    modifier = Modifier.background(
                        brush = Brush.verticalGradient(
                            colors = if (isDark) {
                                listOf(
                                    Color(0xFF1E3A5F).copy(alpha = 0.8f),
                                    Color(0xFF1E3A5F).copy(alpha = 0.6f)
                                )
                            } else {
                                listOf(
                                    Color(0xFFE8F0FE).copy(alpha = 0.9f),
                                    Color(0xFFD2E3FC).copy(alpha = 0.7f)
                                )
                            }
                        )
                    )
                ) {
                    Box(
                        modifier = Modifier.background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.1f),
                                    Color.Transparent
                                )
                            )
                        )
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                lineHeight = 22.sp
                            ),
                            color = if (isDark) Color.White else Color(0xFF202124),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall,
                color = if (isDark) {
                    Color.White.copy(alpha = 0.5f)
                } else {
                    Color.Black.copy(alpha = 0.5f)
                }
            )
        }
    }
}

@Composable
private fun AIMessageWithContent(
    thinking: String?,
    content: List<MessageContent>,
    time: String,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    fullMessageText: String = ""
) {
    var isThinkingExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Thinking section at top if present
        if (thinking != null && !isStreaming) {
            ThinkingSection(
                thinking = thinking,
                isExpanded = isThinkingExpanded,
                onToggle = { isThinkingExpanded = !isThinkingExpanded },
                isDark = isDark
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Main content - clean layout without avatar
        Column(modifier = Modifier.fillMaxWidth()) {
            content.forEach { item ->
                when (item) {
                    is MessageContent.Text -> {
                        Text(
                            text = parseStyledText(item.text),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 15.sp,
                                lineHeight = 24.sp,
                                letterSpacing = 0.sp
                            ),
                            color = if (isDark) {
                                Color.White.copy(alpha = 0.92f)
                            } else {
                                Color(0xFF1F1F1F)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }
                    is MessageContent.Heading -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        HeadingText(text = item.text, level = item.level, isDark = isDark)
                    }
                    is MessageContent.BulletList -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        BulletListText(items = item.items, isDark = isDark)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    is MessageContent.Table -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        TableContent(
                            headers = item.headers,
                            rows = item.rows,
                            isDark = isDark
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    is MessageContent.CodeBlock -> {
                        if (item.language == "inline") {
                            InlineCodeText(code = item.code, isDark = isDark)
                        } else {
                            Spacer(modifier = Modifier.height(12.dp))
                            CodeBlockCard(
                                isStreaming = isStreaming,
                                code = item.code,
                                language = item.language,
                                isDark = isDark
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }

        // Bottom action bar - Copy button and timestamp (like Gemini)
        if (!isStreaming && fullMessageText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Timestamp on left
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) {
                        Color.White.copy(alpha = 0.4f)
                    } else {
                        Color.Black.copy(alpha = 0.4f)
                    }
                )

                // Copy button on right
                Surface(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("AI Response", fullMessageText))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            modifier = Modifier.size(14.dp),
                            tint = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "Copy",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun TableContent(
    headers: List<String>,
    rows: List<List<String>>,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Calculate column width (equal for all)
    val columnWidth = 140.dp

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isDark) {
            Color(0xFF2D2D2D).copy(alpha = 0.7f)
        } else {
            Color(0xFFF1F3F4).copy(alpha = 0.9f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isDark) {
                Color.White.copy(alpha = 0.2f)
            } else {
                Color.Black.copy(alpha = 0.2f)
            }
        ),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header with copy button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "📊", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = "Table",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) Color(0xFF8AB4F8) else Color(0xFF1A73E8)
                    )
                    Text(
                        text = "${rows.size} rows",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
                    )
                }

                IconButton(
                    onClick = {
                        val csv = buildString {
                            append(headers.joinToString(","))
                            append("\n")
                            rows.forEach { row ->
                                append(row.joinToString(","))
                                append("\n")
                            }
                        }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Table", csv))
                        Toast.makeText(context, "Table copied as CSV!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(16.dp),
                        tint = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.size(8.dp))
            HorizontalDivider(
                color = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f)
            )
            Spacer(modifier = Modifier.size(12.dp))

            // Table with proper grid
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .border(
                        width = 1.dp,
                        color = if (isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                Column {
                    // Header Row
                    Row(
                        modifier = Modifier.background(
                            color = if (isDark) Color(0xFF1A1A1A).copy(alpha = 0.6f) else Color(0xFFE8EAED)
                        )
                    ) {
                        headers.forEachIndexed { index, header ->
                            Box(
                                modifier = Modifier
                                    .width(columnWidth)
                                    .height(48.dp)
                                    .then(
                                        if (index < headers.size - 1) {
                                            Modifier.drawBehind {
                                                drawLine(
                                                    color = if (isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.25f),
                                                    start = Offset(size.width, 0f),
                                                    end = Offset(size.width, size.height),
                                                    strokeWidth = 1.dp.toPx()
                                                )
                                            }
                                        } else Modifier
                                    )
                                    .padding(12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = header,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color(0xFF8AB4F8) else Color(0xFF1A73E8),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Horizontal divider after header
                    HorizontalDivider(
                        thickness = 2.dp,
                        color = if (isDark) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.3f)
                    )

                    // Data Rows
                    rows.forEachIndexed { rowIndex, row ->
                        Row {
                            row.forEachIndexed { cellIndex, cell ->
                                Box(
                                    modifier = Modifier
                                        .width(columnWidth)
                                        .heightIn(min = 44.dp)
                                        .then(
                                            if (cellIndex < row.size - 1) {
                                                Modifier.drawBehind {
                                                    drawLine(
                                                        color = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f),
                                                        start = Offset(size.width, 0f),
                                                        end = Offset(size.width, size.height),
                                                        strokeWidth = 1.dp.toPx()
                                                    )
                                                }
                                            } else Modifier
                                        )
                                        .padding(12.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = cell,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isDark) Color.White.copy(alpha = 0.9f) else Color(0xFF202124),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Horizontal divider between rows (except last)
                        if (rowIndex < rows.size - 1) {
                            HorizontalDivider(
                                color = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f)
                            )
                        }
                    }
                }
            }
        }
    }
}



@Composable
private fun HeadingText(
    text: String,
    level: Int,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val style = when (level) {
        1 -> MaterialTheme.typography.headlineMedium
        2 -> MaterialTheme.typography.titleLarge
        else -> MaterialTheme.typography.titleMedium
    }

    Text(
        text = parseStyledText(text),
        style = style,
        fontWeight = FontWeight.Bold,
        color = if (isDark) {
            Color.White.copy(alpha = 0.95f)
        } else {
            Color(0xFF202124).copy(alpha = 0.95f)
        },
        modifier = modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun BulletListText(
    items: List<String>,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(vertical = 6.dp)) {
        items.forEach { item ->
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "  •  ",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.5f)
                )
                Text(
                    text = parseStyledText(item),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        letterSpacing = 0.sp
                    ),
                    color = if (isDark) {
                        Color.White.copy(alpha = 0.92f)
                    } else {
                        Color(0xFF1F1F1F)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}


@Composable
private fun parseStyledText(text: String): AnnotatedString {
    return buildAnnotatedString {
        val boldRegex = """\*\*(.+?)\*\*""".toRegex()
        var lastEnd = 0

        boldRegex.findAll(text).forEach { match ->
            if (match.range.first > lastEnd) {
                append(text.substring(lastEnd, match.range.first))
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(match.groupValues[1])
            }
            lastEnd = match.range.last + 1
        }

        if (lastEnd < text.length) {
            append(text.substring(lastEnd))
        }
    }
}

@Composable
private fun CodeBlockCard(
    isStreaming: Boolean = false,
    code: String,
    language: String,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val codeLines = code.lines()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF6F8FA),
        border = BorderStroke(
            width = 1.dp,
            color = if (isDark) Color(0xFF3D3D3D) else Color(0xFFD0D7DE)
        )
    ) {
        Column {
            // Compact header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDark) Color(0xFF2D2D2D) else Color(0xFFEFF2F5))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifEmpty { "code" },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.6f)
                )

                if (!isStreaming) {
                    Surface(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Code", code))
                            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                modifier = Modifier.size(14.dp),
                                tint = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "Copy",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            // Code content with line numbers
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 12.dp)
            ) {
                Row {
                    // Line numbers column
                    Column(
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        codeLines.forEachIndexed { index, _ ->
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp
                                ),
                                color = if (isDark) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.3f)
                            )
                        }
                    }

                    // Vertical divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height((codeLines.size * 20).dp)
                            .background(if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f))
                    )

                    // Code column
                    Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp)) {
                        codeLines.forEach { line ->
                            Text(
                                text = line.ifEmpty { " " },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp
                                ),
                                color = if (isDark) Color(0xFFE6E6E6) else Color(0xFF24292F)
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun InlineCodeText(
    code: String,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Text(
        text = buildAnnotatedString {
            withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = if (isDark) {
                        Color(0xFF2D2D2D).copy(alpha = 0.6f)
                    } else {
                        Color(0xFFF1F3F4).copy(alpha = 0.8f)
                    },
                    color = if (isDark) Color(0xFF8AB4F8) else Color(0xFF1A73E8)
                )
            ) {
                append(" $code ")
            }
        },
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier
    )
}

@Composable
private fun ThinkingSection(
    thinking: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            color = if (isDark) {
                Color.White.copy(alpha = 0.15f)
            } else {
                Color.Black.copy(alpha = 0.15f)
            }
        ),
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier.background(
                brush = Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(
                            Color(0xFF2D2D2D).copy(alpha = 0.6f),
                            Color(0xFF1A1A1A).copy(alpha = 0.4f)
                        )
                    } else {
                        listOf(
                            Color(0xFFF8F9FA).copy(alpha = 0.9f),
                            Color(0xFFE8EAED).copy(alpha = 0.7f)
                        )
                    }
                )
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggle),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🧠",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "Thought process",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color(0xFF8AB4F8) else Color(0xFF1A73E8)
                        )
                        Text(
                            text = "(${thinking.split("\\s+".toRegex()).size} words)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) {
                                Color.White.copy(alpha = 0.6f)
                            } else {
                                Color.Black.copy(alpha = 0.6f)
                            }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Thinking", thinking))
                                Toast.makeText(context, "Thinking copied!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                modifier = Modifier.size(16.dp),
                                tint = if (isDark) {
                                    Color.White.copy(alpha = 0.6f)
                                } else {
                                    Color.Black.copy(alpha = 0.6f)
                                }
                            )
                        }

                        IconButton(
                            onClick = onToggle,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = if (isDark) Color(0xFF8AB4F8) else Color(0xFF1A73E8)
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.size(8.dp))
                        HorizontalDivider(
                            color = if (isDark) {
                                Color.White.copy(alpha = 0.15f)
                            } else {
                                Color.Black.copy(alpha = 0.15f)
                            }
                        )
                        Spacer(modifier = Modifier.size(12.dp))

                        Text(
                            text = thinking,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            ),
                            color = if (isDark) {
                                Color.White.copy(alpha = 0.8f)
                            } else {
                                Color(0xFF202124).copy(alpha = 0.8f)
                            },
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StreamingMessageItem(
    text: String,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    
    // Parse content for real-time markdown rendering
    val parsed = remember(text) { parseMessageContent(text) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Render parsed content with markdown
        Column(modifier = Modifier.fillMaxWidth()) {
            parsed.content.forEach { item ->
                when (item) {
                    is MessageContent.Text -> {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = parseStyledText(item.text),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 15.sp,
                                    lineHeight = 24.sp,
                                    letterSpacing = 0.sp
                                ),
                                color = if (isDark) {
                                    Color.White.copy(alpha = 0.92f)
                                } else {
                                    Color(0xFF1F1F1F)
                                }
                            )
                            BlinkingCursor(isDark = isDark)
                        }
                    }
                    is MessageContent.Heading -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        HeadingText(text = item.text, level = item.level, isDark = isDark)
                    }
                    is MessageContent.BulletList -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        BulletListText(items = item.items, isDark = isDark)
                    }
                    is MessageContent.Table -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        TableContent(
                            headers = item.headers,
                            rows = item.rows,
                            isDark = isDark
                        )
                    }
                    is MessageContent.CodeBlock -> {
                        if (item.language == "inline") {
                            InlineCodeText(code = item.code, isDark = isDark)
                        } else {
                            Spacer(modifier = Modifier.height(12.dp))
                            CodeBlockCard(
                                isStreaming = true,
                                code = item.code,
                                language = item.language,
                                isDark = isDark
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun BlinkingCursor(isDark: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 530),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    Text(
        text = "▊",
        style = MaterialTheme.typography.bodyLarge,
        color = if (isDark) {
            Color(0xFF8AB4F8).copy(alpha = alpha)
        } else {
            Color(0xFF1A73E8).copy(alpha = alpha)
        },
        modifier = Modifier.padding(start = 2.dp)
    )
}
