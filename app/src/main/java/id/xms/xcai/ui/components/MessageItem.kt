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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.layout.ContentScale
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
import coil.compose.AsyncImage
import id.xms.xcai.data.local.ChatEntity
import id.xms.xcai.ui.theme.Web3Cyan
import id.xms.xcai.ui.theme.Web3Slate
import id.xms.xcai.ui.theme.Web3TextPrimary
import id.xms.xcai.ui.theme.Web3TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Data classes
data class ParsedMessage(val thinking: String?, val content: List<MessageContent>)

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

    val cleanMessage =
            if (thinking != null) {
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
    val headers =
            line.split("|")
                    .drop(1) // Remove first empty element from leading |
                    .dropLast(1) // Remove last empty element from trailing |
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

        val cells =
                rowLine.split("|")
                        .drop(1) // Remove first empty
                        .dropLast(1) // Remove last empty
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
        streamingText: String = "",
        onEdit: ((ChatEntity) -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeString = timeFormat.format(Date(message.timestamp))

    if (message.isUser) {
        UserMessageBubble(
                message = message.message,
                imageUri = message.imageUri,
                time = timeString,
                isDark = isDark,
                onEdit = { onEdit?.invoke(message) },
                modifier = modifier
        )
    } else {
        val messageToShow = if (isStreaming) streamingText else message.message
        val parsed = remember(messageToShow) { parseMessageContent(messageToShow) }
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
        imageUri: String?,
        time: String,
        isDark: Boolean,
        onEdit: () -> Unit,
        modifier: Modifier = Modifier
) {
    Row(
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End
    ) {
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 300.dp)) {
            // Gradient Glass Bubble for User
            Box(
                    modifier =
                            Modifier.clip(RoundedCornerShape(26.dp, 26.dp, 4.dp, 26.dp))
                                    .background(
                                            brush =
                                                    Brush.linearGradient(
                                                            colors =
                                                                    listOf(
                                                                            Web3Cyan.copy(
                                                                                    alpha = 0.25f
                                                                            ),
                                                                            Web3Cyan.copy(
                                                                                    alpha = 0.1f
                                                                            )
                                                                    )
                                                    )
                                    )
                                    .border(
                                            width = 1.dp,
                                            brush =
                                                    Brush.linearGradient(
                                                            colors =
                                                                    listOf(
                                                                            Web3Cyan.copy(
                                                                                    alpha = 0.4f
                                                                            ),
                                                                            Web3Cyan.copy(
                                                                                    alpha = 0.1f
                                                                            )
                                                                    )
                                                    ),
                                            shape = RoundedCornerShape(26.dp, 26.dp, 4.dp, 26.dp)
                                    )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Show image preview if available
                    if (!imageUri.isNullOrEmpty()) {
                        AsyncImage(
                                model = imageUri,
                                contentDescription = "Attached image",
                                modifier =
                                        Modifier.widthIn(max = 250.dp)
                                                .heightIn(max = 200.dp)
                                                .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Fit
                        )
                        if (message.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Show text message if not empty
                    if (message.isNotEmpty()) {
                        Text(
                                text = message,
                                style =
                                        MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = 16.sp,
                                                lineHeight = 22.sp
                                        ),
                                color = Web3TextPrimary,
                                modifier = Modifier.padding(2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.size(4.dp))

            // Time and Edit button row
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onEdit, modifier = Modifier.size(16.dp)) {
                    Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = Web3TextSecondary.copy(alpha = 0.7f),
                            modifier = Modifier.size(12.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall,
                        color = Web3TextSecondary.copy(alpha = 0.6f)
                )
            }
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

    Row(
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Start
    ) {
        // AI Avatar or Icon (Optional, can be added here)

        Column(modifier = Modifier.weight(1f)) {
            // Thinking Section
            if (thinking != null && !isStreaming) {
                ThinkingSection(
                        thinking = thinking,
                        isExpanded = isThinkingExpanded,
                        onToggle = { isThinkingExpanded = !isThinkingExpanded },
                        isDark = isDark
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // AI Message Bubble Background
            Box(
                    modifier =
                            Modifier.clip(RoundedCornerShape(4.dp, 24.dp, 24.dp, 24.dp))
                                    .background(Web3Slate.copy(alpha = 0.3f))
                                    .padding(16.dp)
            ) {
                SelectionContainer {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        content.forEach { item ->
                            when (item) {
                                is MessageContent.Text -> {
                                    Text(
                                            text = parseStyledText(item.text),
                                            style =
                                                    MaterialTheme.typography.bodyLarge.copy(
                                                            fontSize = 15.sp,
                                                            lineHeight = 24.sp,
                                                            letterSpacing = 0.sp
                                                    ),
                                            color = Web3TextPrimary,
                                            modifier =
                                                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    )
                                }
                                is MessageContent.Heading -> {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    HeadingText(
                                            text = item.text,
                                            level = item.level,
                                            isDark = isDark
                                    )
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
                }
            }

            // Bottom action bar
            if (!isStreaming && fullMessageText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            text = time,
                            style = MaterialTheme.typography.labelSmall,
                            color = Web3TextSecondary
                    )

                    Surface(
                            onClick = {
                                val clipboard =
                                        context.getSystemService(Context.CLIPBOARD_SERVICE) as
                                                ClipboardManager
                                clipboard.setPrimaryClip(
                                        ClipData.newPlainText("AI Response", fullMessageText)
                                )
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
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
                                    tint = Web3TextSecondary
                            )
                            Text(
                                    "Copy",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Web3TextSecondary
                            )
                        }
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
            modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            color = Web3Slate.copy(alpha = 0.7f),
            border = BorderStroke(width = 1.dp, color = Web3Cyan.copy(alpha = 0.2f)),
            shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
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
                            color = Web3Cyan
                    )
                    Text(
                            text = "${rows.size} rows",
                            style = MaterialTheme.typography.labelSmall,
                            color = Web3TextSecondary
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
                            val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as
                                            ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Table", csv))
                            Toast.makeText(context, "Table copied as CSV!", Toast.LENGTH_SHORT)
                                    .show()
                        },
                        modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            modifier = Modifier.size(16.dp),
                            tint = Web3TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.size(8.dp))
            HorizontalDivider(color = Web3TextSecondary.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.size(12.dp))

            // Table with proper grid
            Box(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .border(
                                            width = 1.dp,
                                            color = Web3TextSecondary.copy(alpha = 0.25f),
                                            shape = RoundedCornerShape(8.dp)
                                    )
            ) {
                Column {
                    // Header Row
                    Row(
                            modifier =
                                    Modifier.background(
                                            color =
                                                    if (isDark) Color(0xFF1A1A1A).copy(alpha = 0.6f)
                                                    else Color(0xFFE8EAED)
                                    )
                    ) {
                        headers.forEachIndexed { index, header ->
                            Box(
                                    modifier =
                                            Modifier.width(columnWidth)
                                                    .height(48.dp)
                                                    .then(
                                                            if (index < headers.size - 1) {
                                                                Modifier.drawBehind {
                                                                    drawLine(
                                                                            color =
                                                                                    if (isDark)
                                                                                            Color.White
                                                                                                    .copy(
                                                                                                            alpha =
                                                                                                                    0.25f
                                                                                                    )
                                                                                    else
                                                                                            Color.Black
                                                                                                    .copy(
                                                                                                            alpha =
                                                                                                                    0.25f
                                                                                                    ),
                                                                            start =
                                                                                    Offset(
                                                                                            size.width,
                                                                                            0f
                                                                                    ),
                                                                            end =
                                                                                    Offset(
                                                                                            size.width,
                                                                                            size.height
                                                                                    ),
                                                                            strokeWidth =
                                                                                    1.dp.toPx()
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
                                        color =
                                                if (isDark) Color(0xFF8AB4F8)
                                                else Color(0xFF1A73E8),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Horizontal divider after header
                    HorizontalDivider(
                            thickness = 2.dp,
                            color =
                                    if (isDark) Color.White.copy(alpha = 0.3f)
                                    else Color.Black.copy(alpha = 0.3f)
                    )

                    // Data Rows
                    rows.forEachIndexed { rowIndex, row ->
                        Row {
                            row.forEachIndexed { cellIndex, cell ->
                                Box(
                                        modifier =
                                                Modifier.width(columnWidth)
                                                        .heightIn(min = 44.dp)
                                                        .then(
                                                                if (cellIndex < row.size - 1) {
                                                                    Modifier.drawBehind {
                                                                        drawLine(
                                                                                color =
                                                                                        if (isDark)
                                                                                                Color.White
                                                                                                        .copy(
                                                                                                                alpha =
                                                                                                                        0.15f
                                                                                                        )
                                                                                        else
                                                                                                Color.Black
                                                                                                        .copy(
                                                                                                                alpha =
                                                                                                                        0.15f
                                                                                                        ),
                                                                                start =
                                                                                        Offset(
                                                                                                size.width,
                                                                                                0f
                                                                                        ),
                                                                                end =
                                                                                        Offset(
                                                                                                size.width,
                                                                                                size.height
                                                                                        ),
                                                                                strokeWidth =
                                                                                        1.dp.toPx()
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
                                            color =
                                                    if (isDark) Color.White.copy(alpha = 0.9f)
                                                    else Color(0xFF202124),
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Horizontal divider between rows (except last)
                        if (rowIndex < rows.size - 1) {
                            HorizontalDivider(
                                    color =
                                            if (isDark) Color.White.copy(alpha = 0.15f)
                                            else Color.Black.copy(alpha = 0.15f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeadingText(text: String, level: Int, isDark: Boolean, modifier: Modifier = Modifier) {
    val style =
            when (level) {
                1 -> MaterialTheme.typography.headlineMedium
                2 -> MaterialTheme.typography.titleLarge
                else -> MaterialTheme.typography.titleMedium
            }

    Text(
            text = parseStyledText(text),
            style = style,
            fontWeight = FontWeight.Bold,
            color =
                    if (isDark) {
                        Color.White.copy(alpha = 0.95f)
                    } else {
                        Color(0xFF202124).copy(alpha = 0.95f)
                    },
            modifier = modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun BulletListText(items: List<String>, isDark: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = 6.dp)) {
        items.forEach { item ->
            Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Text(
                        text = "  •  ",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        color =
                                if (isDark) Color.White.copy(alpha = 0.6f)
                                else Color.Black.copy(alpha = 0.5f)
                )
                Text(
                        text = parseStyledText(item),
                        style =
                                MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = 15.sp,
                                        lineHeight = 24.sp,
                                        letterSpacing = 0.sp
                                ),
                        color =
                                if (isDark) {
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
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[1]) }
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
            border =
                    BorderStroke(
                            width = 1.dp,
                            color = if (isDark) Color(0xFF3D3D3D) else Color(0xFFD0D7DE)
                    )
    ) {
        Column {
            // Compact header
            Row(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .background(
                                            if (isDark) Color(0xFF2D2D2D) else Color(0xFFEFF2F5)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                        text = language.ifEmpty { "code" },
                        style = MaterialTheme.typography.labelMedium,
                        color =
                                if (isDark) Color.White.copy(alpha = 0.7f)
                                else Color.Black.copy(alpha = 0.6f)
                )

                if (!isStreaming) {
                    Surface(
                            onClick = {
                                val clipboard =
                                        context.getSystemService(Context.CLIPBOARD_SERVICE) as
                                                ClipboardManager
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
                                    tint =
                                            if (isDark) Color.White.copy(alpha = 0.6f)
                                            else Color.Black.copy(alpha = 0.5f)
                            )
                            Text(
                                    text = "Copy",
                                    style = MaterialTheme.typography.labelSmall,
                                    color =
                                            if (isDark) Color.White.copy(alpha = 0.6f)
                                            else Color.Black.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            // Code content with line numbers
            Box(
                    modifier =
                            Modifier.fillMaxWidth()
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
                                    style =
                                            MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 13.sp,
                                                    lineHeight = 20.sp
                                            ),
                                    color =
                                            if (isDark) Color.White.copy(alpha = 0.3f)
                                            else Color.Black.copy(alpha = 0.3f)
                            )
                        }
                    }

                    // Vertical divider
                    Box(
                            modifier =
                                    Modifier.width(1.dp)
                                            .height((codeLines.size * 20).dp)
                                            .background(
                                                    if (isDark) Color.White.copy(alpha = 0.1f)
                                                    else Color.Black.copy(alpha = 0.1f)
                                            )
                    )

                    // Code column
                    Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp)) {
                        codeLines.forEach { line ->
                            Text(
                                    text = line.ifEmpty { " " },
                                    style =
                                            MaterialTheme.typography.bodySmall.copy(
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
private fun InlineCodeText(code: String, isDark: Boolean, modifier: Modifier = Modifier) {
    Text(
            text =
                    buildAnnotatedString {
                        withStyle(
                                SpanStyle(
                                        fontFamily = FontFamily.Monospace,
                                        background =
                                                if (isDark) {
                                                    Color(0xFF2D2D2D).copy(alpha = 0.6f)
                                                } else {
                                                    Color(0xFFF1F3F4).copy(alpha = 0.8f)
                                                },
                                        color = if (isDark) Color(0xFF8AB4F8) else Color(0xFF1A73E8)
                                )
                        ) { append(" $code ") }
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

    // Calculate analysis summary
    val wordCount = thinking.split("\\s+".toRegex()).size
    val analysisLabel =
            when {
                wordCount > 200 -> "Deep Analysis"
                wordCount > 50 -> "Analyzed"
                else -> "Quick Thought"
            }

    Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent
    ) {
        Box(
                modifier =
                        Modifier.background(
                                brush =
                                        Brush.horizontalGradient(
                                                colors =
                                                        if (isDark) {
                                                            listOf(
                                                                    Color(0xFF1A237E)
                                                                            .copy(alpha = 0.3f),
                                                                    Color(0xFF311B92)
                                                                            .copy(alpha = 0.2f),
                                                                    Color(0xFF4A148C)
                                                                            .copy(alpha = 0.15f)
                                                            )
                                                        } else {
                                                            listOf(
                                                                    Color(0xFFE8EAF6)
                                                                            .copy(alpha = 0.9f),
                                                                    Color(0xFFEDE7F6)
                                                                            .copy(alpha = 0.8f),
                                                                    Color(0xFFF3E5F5)
                                                                            .copy(alpha = 0.7f)
                                                            )
                                                        }
                                        )
                        )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Sparkle icon like Gemini
                        Text(text = "✨", style = MaterialTheme.typography.titleMedium)
                        Column {
                            Text(
                                    text = analysisLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color(0xFFB388FF) else Color(0xFF6200EA)
                            )
                            Text(
                                    text =
                                            "$wordCount words • Tap to ${if (isExpanded) "hide" else "view"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color =
                                            if (isDark) {
                                                Color.White.copy(alpha = 0.5f)
                                            } else {
                                                Color.Black.copy(alpha = 0.5f)
                                            }
                            )
                        }
                    }

                    Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                                onClick = {
                                    val clipboard =
                                            context.getSystemService(Context.CLIPBOARD_SERVICE) as
                                                    ClipboardManager
                                    clipboard.setPrimaryClip(
                                            ClipData.newPlainText("Analysis", thinking)
                                    )
                                    Toast.makeText(context, "Analysis copied!", Toast.LENGTH_SHORT)
                                            .show()
                                },
                                modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    modifier = Modifier.size(18.dp),
                                    tint = if (isDark) Color(0xFFB388FF) else Color(0xFF6200EA)
                            )
                        }

                        Surface(
                                shape = CircleShape,
                                color =
                                        if (isDark) Color.White.copy(alpha = 0.1f)
                                        else Color.Black.copy(alpha = 0.05f),
                                modifier = Modifier.size(32.dp)
                        ) {
                            IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
                                Icon(
                                        imageVector =
                                                if (isExpanded) Icons.Default.ExpandLess
                                                else Icons.Default.ExpandMore,
                                        contentDescription =
                                                if (isExpanded) "Collapse" else "Expand",
                                        tint = if (isDark) Color(0xFFB388FF) else Color(0xFF6200EA),
                                        modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                        visible = isExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.size(10.dp))
                        HorizontalDivider(
                                color =
                                        if (isDark) {
                                            Color(0xFFB388FF).copy(alpha = 0.2f)
                                        } else {
                                            Color(0xFF6200EA).copy(alpha = 0.15f)
                                        }
                        )
                        Spacer(modifier = Modifier.size(12.dp))

                        // Content with cleaner styling
                        Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color =
                                        if (isDark) Color(0xFF1A1A1A).copy(alpha = 0.5f)
                                        else Color.White.copy(alpha = 0.7f),
                                border =
                                        BorderStroke(
                                                width = 1.dp,
                                                color =
                                                        if (isDark) Color.White.copy(alpha = 0.08f)
                                                        else Color.Black.copy(alpha = 0.08f)
                                        )
                        ) {
                            Text(
                                    text = thinking,
                                    style =
                                            MaterialTheme.typography.bodyMedium.copy(
                                                    fontSize = 13.sp,
                                                    lineHeight = 20.sp,
                                                    letterSpacing = 0.2.sp
                                            ),
                                    color =
                                            if (isDark) {
                                                Color.White.copy(alpha = 0.85f)
                                            } else {
                                                Color(0xFF37474F)
                                            },
                                    modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StreamingMessageItem(text: String, modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()

    // Check if currently in thinking mode (has opening <think> but no closing </think>)
    val isThinking = text.contains("<think>") && !text.contains("</think>")

    // Check if thinking is complete (has both opening and closing tags)
    val hasCompletedThinking = text.contains("<think>") && text.contains("</think>")

    // Parse content for real-time markdown rendering
    val parsed = remember(text) { parseMessageContent(text) }

    // Check if there's actual content after thinking (not just empty)
    val hasContentAfterThinking =
            parsed.content.any {
                when (it) {
                    is MessageContent.Text -> it.text.isNotBlank()
                    else -> true
                }
            }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // ONLY show "Analyzing..." indicator when AI is actively thinking
        if (isThinking) {
            StreamingThinkingIndicator(isDark = isDark)
            // Don't show anything else while thinking - wait for thinking to complete
        } else {
            // Thinking is complete OR there's no thinking at all

            // Show completed thinking section FIRST if thinking is done
            if (hasCompletedThinking && parsed.thinking != null) {
                var isThinkingExpanded by remember { mutableStateOf(false) }
                ThinkingSection(
                        thinking = parsed.thinking,
                        isExpanded = isThinkingExpanded,
                        onToggle = { isThinkingExpanded = !isThinkingExpanded },
                        isDark = isDark
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // THEN render the output content (parsed content already excludes think tags)
            Column(modifier = Modifier.fillMaxWidth()) {
                parsed.content.forEach { item ->
                    when (item) {
                        is MessageContent.Text -> {
                            // Skip empty text that might result from think tag removal
                            if (item.text.isNotBlank()) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                            text = parseStyledText(item.text),
                                            style =
                                                    MaterialTheme.typography.bodyLarge.copy(
                                                            fontSize = 15.sp,
                                                            lineHeight = 24.sp,
                                                            letterSpacing = 0.sp
                                                    ),
                                            color =
                                                    if (isDark) {
                                                        Color.White.copy(alpha = 0.92f)
                                                    } else {
                                                        Color(0xFF1F1F1F)
                                                    }
                                    )
                                    if (!isThinking) {
                                        BlinkingCursor(isDark = isDark)
                                    }
                                }
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
                            TableContent(headers = item.headers, rows = item.rows, isDark = isDark)
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
        } // Close else block for !isThinking
    }
}

@Composable
private fun StreamingThinkingIndicator(isDark: Boolean, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking_pulse")

    val pulseAlpha by
            infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 1f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation = tween(durationMillis = 800),
                                    repeatMode = RepeatMode.Reverse
                            ),
                    label = "thinking_pulse_alpha"
            )

    Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent
    ) {
        Box(
                modifier =
                        Modifier.background(
                                brush =
                                        Brush.horizontalGradient(
                                                colors =
                                                        if (isDark) {
                                                            listOf(
                                                                    Color(0xFF1A237E)
                                                                            .copy(
                                                                                    alpha =
                                                                                            0.3f *
                                                                                                    pulseAlpha
                                                                            ),
                                                                    Color(0xFF311B92)
                                                                            .copy(
                                                                                    alpha =
                                                                                            0.2f *
                                                                                                    pulseAlpha
                                                                            ),
                                                                    Color(0xFF4A148C)
                                                                            .copy(
                                                                                    alpha =
                                                                                            0.15f *
                                                                                                    pulseAlpha
                                                                            )
                                                            )
                                                        } else {
                                                            listOf(
                                                                    Color(0xFFE8EAF6)
                                                                            .copy(alpha = 0.9f),
                                                                    Color(0xFFEDE7F6)
                                                                            .copy(alpha = 0.8f),
                                                                    Color(0xFFF3E5F5)
                                                                            .copy(alpha = 0.7f)
                                                            )
                                                        }
                                        )
                        )
        ) {
            Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                // Animated sparkle icon
                Text(
                        text = "✨",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.alpha(pulseAlpha)
                )
                Column {
                    Text(
                            text = "Analyzing...",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFFB388FF) else Color(0xFF6200EA)
                    )
                    Text(
                            text = "AI is thinking",
                            style = MaterialTheme.typography.labelSmall,
                            color =
                                    if (isDark) {
                                        Color.White.copy(alpha = 0.5f)
                                    } else {
                                        Color.Black.copy(alpha = 0.5f)
                                    }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Spinning indicator
                CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = if (isDark) Color(0xFFB388FF) else Color(0xFF6200EA),
                        strokeWidth = 2.dp
                )
            }
        }
    }
}

@Composable
private fun BlinkingCursor(isDark: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")

    val alpha by
            infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation = tween(durationMillis = 530),
                                    repeatMode = RepeatMode.Reverse
                            ),
                    label = "cursor_alpha"
            )

    Text(
            text = "▊",
            style = MaterialTheme.typography.bodyLarge,
            color =
                    if (isDark) {
                        Color(0xFF8AB4F8).copy(alpha = alpha)
                    } else {
                        Color(0xFF1A73E8).copy(alpha = alpha)
                    },
            modifier = Modifier.padding(start = 2.dp)
    )
}
