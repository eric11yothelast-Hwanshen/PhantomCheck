package com.phantom.checker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 輕量 Markdown 渲染元件（支援標題、粗體、斜體、刪除線、行內代碼、連結、清單、巢狀清單、任務清單、引用、分隔線、表格與程式碼區塊）。
 */
@Composable
fun PhantomMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = LocalTextStyle.current
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val codeBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val inlineCodeBg = MaterialTheme.colorScheme.surfaceVariant
    val linkColor = MaterialTheme.colorScheme.primary
    val quoteBarColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    val quoteBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val tableBorderColor = MaterialTheme.colorScheme.outlineVariant
    val tableHeaderBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    val uriHandler = LocalUriHandler.current

    val openUrl: (String) -> Unit = remember(uriHandler) {
        { url ->
            try {
                uriHandler.openUri(url)
            } catch (_: Exception) {
                // 無法開啟時靜默降級
            }
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val headingStyle = when (block.level) {
                        1 -> MaterialTheme.typography.titleMedium
                        2 -> MaterialTheme.typography.titleSmall
                        else -> MaterialTheme.typography.bodyLarge
                    }.copy(fontWeight = FontWeight.Bold, color = color)
                    Text(
                        text = parseInlineMarkdown(block.text, inlineCodeBg, linkColor, openUrl),
                        style = headingStyle
                    )
                }
                is MdBlock.Bullet -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (block.indentLevel * 16).dp)
                    ) {
                        Text(
                            text = block.marker,
                            style = style.copy(color = color, fontWeight = FontWeight.Bold)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = parseInlineMarkdown(block.text, inlineCodeBg, linkColor, openUrl),
                            style = style.copy(color = color),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MdBlock.TaskItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (block.indentLevel * 16).dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = if (block.checked) "☑" else "☐",
                            style = style.copy(
                                color = if (block.checked) MaterialTheme.colorScheme.primary else color,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = parseInlineMarkdown(block.text, inlineCodeBg, linkColor, openUrl),
                            style = style.copy(
                                color = if (block.checked) color.copy(alpha = 0.7f) else color,
                                textDecoration = if (block.checked) TextDecoration.LineThrough else TextDecoration.None
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MdBlock.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                            .background(quoteBg)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(quoteBarColor)
                        )
                        Text(
                            text = parseInlineMarkdown(block.text, inlineCodeBg, linkColor, openUrl),
                            style = style.copy(color = color.copy(alpha = 0.88f)),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                is MdBlock.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = color.copy(alpha = 0.22f)
                    )
                }
                is MdBlock.Table -> {
                    MarkdownTableBlock(
                        table = block,
                        textColor = color,
                        style = style,
                        inlineCodeBg = inlineCodeBg,
                        linkColor = linkColor,
                        borderColor = tableBorderColor,
                        headerBg = tableHeaderBg,
                        onOpenUrl = openUrl
                    )
                }
                is MdBlock.CodeBlock -> {
                    Text(
                        text = block.code,
                        style = style.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = color
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(codeBg)
                            .padding(8.dp)
                    )
                }
                is MdBlock.Paragraph -> {
                    Text(
                        text = parseInlineMarkdown(block.text, inlineCodeBg, linkColor, openUrl),
                        style = style.copy(color = color)
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownTableBlock(
    table: MdBlock.Table,
    textColor: Color,
    style: TextStyle,
    inlineCodeBg: Color,
    linkColor: Color,
    borderColor: Color,
    headerBg: Color,
    onOpenUrl: (String) -> Unit
) {
    val colWidths = remember(table) { computeTableColumnWidths(table) }
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .horizontalScroll(scrollState)
    ) {
        Column {
            // 表頭
            Row(modifier = Modifier.background(headerBg)) {
                table.headers.forEachIndexed { colIdx, header ->
                    val align = table.alignments.getOrElse(colIdx) { TableAlign.LEFT }
                    TableCell(
                        text = header,
                        width = colWidths[colIdx],
                        textAlign = align.toTextAlign(),
                        style = style.copy(fontWeight = FontWeight.Bold, color = textColor, fontSize = 12.sp),
                        inlineCodeBg = inlineCodeBg,
                        linkColor = linkColor,
                        onOpenUrl = onOpenUrl
                    )
                }
            }
            HorizontalDivider(color = borderColor)
            // 資料列
            table.rows.forEachIndexed { rowIdx, row ->
                Row {
                    table.headers.indices.forEach { colIdx ->
                        val cellText = row.getOrElse(colIdx) { "" }
                        val align = table.alignments.getOrElse(colIdx) { TableAlign.LEFT }
                        TableCell(
                            text = cellText,
                            width = colWidths[colIdx],
                            textAlign = align.toTextAlign(),
                            style = style.copy(color = textColor, fontSize = 12.sp),
                            inlineCodeBg = inlineCodeBg,
                            linkColor = linkColor,
                            onOpenUrl = onOpenUrl
                        )
                    }
                }
                if (rowIdx < table.rows.lastIndex) {
                    HorizontalDivider(color = borderColor.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    width: Dp,
    textAlign: TextAlign,
    style: TextStyle,
    inlineCodeBg: Color,
    linkColor: Color,
    onOpenUrl: (String) -> Unit
) {
    Text(
        text = parseInlineMarkdown(text, inlineCodeBg, linkColor, onOpenUrl),
        style = style.copy(textAlign = textAlign),
        modifier = Modifier
            .width(width)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

private fun computeTableColumnWidths(table: MdBlock.Table): List<Dp> {
    val colCount = table.headers.size
    return List(colCount) { colIdx ->
        var maxUnits = estimateVisualUnits(table.headers[colIdx])
        for (row in table.rows) {
            val cell = row.getOrElse(colIdx) { "" }
            val units = estimateVisualUnits(cell)
            if (units > maxUnits) maxUnits = units
        }
        val estimatedDp = (maxUnits * 7.5f + 24f).dp
        estimatedDp.coerceIn(68.dp, 220.dp)
    }
}

private fun estimateVisualUnits(text: String): Float {
    var units = 0f
    for (ch in text) {
        units += if (ch.code > 0x2E80) 1.85f else 1.0f
    }
    return units
}

private enum class TableAlign {
    LEFT, CENTER, RIGHT;

    fun toTextAlign(): TextAlign = when (this) {
        LEFT -> TextAlign.Start
        CENTER -> TextAlign.Center
        RIGHT -> TextAlign.End
    }
}

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val marker: String, val text: String, val indentLevel: Int = 0) : MdBlock
    data class TaskItem(val checked: Boolean, val text: String, val indentLevel: Int = 0) : MdBlock
    data class Blockquote(val text: String) : MdBlock
    data object Divider : MdBlock
    data class Table(
        val headers: List<String>,
        val alignments: List<TableAlign>,
        val rows: List<List<String>>
    ) : MdBlock
    data class CodeBlock(val code: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
}

private val TABLE_ALIGN_LINE_REGEX = Regex("""^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$""")
private val TABLE_SINGLE_ALIGN_REGEX = Regex("""^\s*\|\s*:?-{3,}:?\s*\|\s*$""")
private val DIVIDER_LINE_REGEX = Regex("""^([-*_])(?:\s*\1){2,}\s*$""")
private val TASK_LINE_REGEX = Regex("""^(\s*)[-*+]\s+\[([ xX])]\s+(.*)$""")
private val ORDERED_LIST_REGEX = Regex("""^(\s*)(\d+\.)\s+(.*)$""")
private val UNORDERED_LIST_REGEX = Regex("""^(\s*)[-*+•]\s+(.*)$""")

private fun isTableAlignLine(line: String): Boolean {
    return TABLE_ALIGN_LINE_REGEX.matches(line) || TABLE_SINGLE_ALIGN_REGEX.matches(line)
}

private fun splitTableRow(line: String): List<String> {
    var s = line.trim()
    if (s.startsWith("|")) s = s.substring(1)
    if (s.endsWith("|")) s = s.substring(0, s.length - 1)
    return s.split("|").map { it.trim() }
}

private fun parseTableAlignments(line: String, colCount: Int): List<TableAlign> {
    val cells = splitTableRow(line)
    return List(colCount) { idx ->
        val raw = cells.getOrElse(idx) { "" }.trim()
        val left = raw.startsWith(":")
        val right = raw.endsWith(":")
        when {
            left && right -> TableAlign.CENTER
            right -> TableAlign.RIGHT
            else -> TableAlign.LEFT
        }
    }
}

private fun leadingIndentLevel(rawLine: String): Int {
    var spaces = 0
    for (ch in rawLine) {
        when (ch) {
            ' ' -> spaces++
            '\t' -> spaces += 2
            else -> break
        }
    }
    return if (spaces >= 2) 1 else 0
}

private fun parseMarkdownBlocks(raw: String): List<MdBlock> {
    val lines = raw.replace("\r\n", "\n").split("\n")
    val result = mutableListOf<MdBlock>()
    var inCode = false
    val codeBuf = StringBuilder()

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            if (inCode) {
                result.add(MdBlock.CodeBlock(codeBuf.toString().trimEnd()))
                codeBuf.clear()
                inCode = false
            } else {
                inCode = true
            }
            i++
            continue
        }
        if (inCode) {
            codeBuf.appendLine(line)
            i++
            continue
        }
        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // 表格偵測：當前行含 '|' 且下一行為對齊行
        if (trimmed.contains("|") && i + 1 < lines.size && isTableAlignLine(lines[i + 1])) {
            val headers = splitTableRow(trimmed)
            val alignments = parseTableAlignments(lines[i + 1], headers.size)
            val rows = mutableListOf<List<String>>()
            var j = i + 2
            while (j < lines.size) {
                val rowTrimmed = lines[j].trim()
                if (rowTrimmed.isEmpty() || !rowTrimmed.contains("|")) break
                val cells = splitTableRow(rowTrimmed)
                val normalized = List(headers.size) { idx -> cells.getOrElse(idx) { "" } }
                rows.add(normalized)
                j++
            }
            result.add(MdBlock.Table(headers, alignments, rows))
            i = j
            continue
        }

        // 引用區塊（合併連續 > 行）
        if (trimmed.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            var j = i
            while (j < lines.size) {
                val qt = lines[j].trim()
                if (!qt.startsWith(">")) break
                quoteLines.add(qt.removePrefix(">").trimStart())
                j++
            }
            result.add(MdBlock.Blockquote(quoteLines.joinToString("\n")))
            i = j
            continue
        }

        // 分隔線
        if (DIVIDER_LINE_REGEX.matches(trimmed)) {
            result.add(MdBlock.Divider)
            i++
            continue
        }

        // 任務清單（須優先於一般條列）
        val taskMatch = TASK_LINE_REGEX.matchEntire(line)
        if (taskMatch != null) {
            val indent = leadingIndentLevel(line)
            val checked = taskMatch.groupValues[2].equals("x", ignoreCase = true)
            result.add(MdBlock.TaskItem(checked, taskMatch.groupValues[3].trim(), indent))
            i++
            continue
        }

        // 標題
        when {
            trimmed.startsWith("### ") -> {
                result.add(MdBlock.Heading(3, trimmed.removePrefix("### ").trim()))
                i++
                continue
            }
            trimmed.startsWith("## ") -> {
                result.add(MdBlock.Heading(2, trimmed.removePrefix("## ").trim()))
                i++
                continue
            }
            trimmed.startsWith("# ") -> {
                result.add(MdBlock.Heading(1, trimmed.removePrefix("# ").trim()))
                i++
                continue
            }
        }

        // 無序清單（支援一層縮排）
        val ulMatch = UNORDERED_LIST_REGEX.matchEntire(line)
        if (ulMatch != null) {
            val indent = leadingIndentLevel(line)
            val marker = if (indent > 0) "◦" else "•"
            result.add(MdBlock.Bullet(marker, ulMatch.groupValues[2].trim(), indent))
            i++
            continue
        }

        // 有序清單（支援一層縮排）
        val olMatch = ORDERED_LIST_REGEX.matchEntire(line)
        if (olMatch != null) {
            val indent = leadingIndentLevel(line)
            result.add(MdBlock.Bullet(olMatch.groupValues[2], olMatch.groupValues[3].trim(), indent))
            i++
            continue
        }

        result.add(MdBlock.Paragraph(trimmed))
        i++
    }

    if (inCode && codeBuf.isNotEmpty()) {
        result.add(MdBlock.CodeBlock(codeBuf.toString().trimEnd()))
    }
    return result
}

private fun parseInlineMarkdown(
    text: String,
    codeBg: Color,
    linkColor: Color,
    onOpenUrl: (String) -> Unit
): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            // 粗體 **...**
            if (i + 1 < text.length && text[i] == '*' && text[i + 1] == '*') {
                val end = text.indexOf("**", startIndex = i + 2)
                if (end > i + 2) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(text.substring(i + 2, end))
                    pop()
                    i = end + 2
                    continue
                }
            }
            // 刪除線 ~~...~~
            if (i + 1 < text.length && text[i] == '~' && text[i + 1] == '~') {
                val end = text.indexOf("~~", startIndex = i + 2)
                if (end > i + 2) {
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    append(text.substring(i + 2, end))
                    pop()
                    i = end + 2
                    continue
                }
            }
            // 行內代碼 `...`
            if (text[i] == '`') {
                val end = text.indexOf('`', startIndex = i + 1)
                if (end > i + 1) {
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBg
                        )
                    )
                    append(text.substring(i + 1, end))
                    pop()
                    i = end + 1
                    continue
                }
            }
            // 連結 [label](url)
            if (text[i] == '[') {
                val closeBracket = text.indexOf(']', startIndex = i + 1)
                if (closeBracket > i + 1 && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', startIndex = closeBracket + 2)
                    if (closeParen > closeBracket + 2) {
                        val label = text.substring(i + 1, closeBracket)
                        val url = text.substring(closeBracket + 2, closeParen).trim()
                        val isWebUrl = url.startsWith("http://", ignoreCase = true) ||
                            url.startsWith("https://", ignoreCase = true)
                        if (isWebUrl) {
                            pushLink(
                                LinkAnnotation.Url(
                                    url = url,
                                    styles = TextLinkStyles(
                                        style = SpanStyle(
                                            color = linkColor,
                                            textDecoration = TextDecoration.Underline
                                        )
                                    ),
                                    linkInteractionListener = { onOpenUrl(url) }
                                )
                            )
                            append(label)
                            pop()
                        } else {
                            // 非合法網址降級顯示純文字
                            append(label)
                        }
                        i = closeParen + 1
                        continue
                    }
                }
            }
            // 斜體 *...*（非 **）
            if (text[i] == '*' && (i + 1 >= text.length || text[i + 1] != '*')) {
                val end = findClosingDelimiter(text, '*', i + 1)
                if (end > i + 1 && text[i + 1] != ' ' && text[end - 1] != ' ') {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(text.substring(i + 1, end))
                    pop()
                    i = end + 1
                    continue
                }
            }
            // 斜體 _..._（避開單字內底線如 foo_bar）
            if (text[i] == '_' && (i == 0 || !text[i - 1].isLetterOrDigit())) {
                val end = findClosingDelimiter(text, '_', i + 1)
                if (end > i + 1 && text[i + 1] != ' ' && text[end - 1] != ' ' &&
                    (end + 1 >= text.length || !text[end + 1].isLetterOrDigit())
                ) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(text.substring(i + 1, end))
                    pop()
                    i = end + 1
                    continue
                }
            }

            append(text[i])
            i++
        }
    }
}

private fun findClosingDelimiter(text: String, delimiter: Char, startIndex: Int): Int {
    var idx = startIndex
    while (idx < text.length) {
        if (text[idx] == delimiter) {
            // 避免把雙符號如 ** 當成單符號結尾
            if (idx + 1 < text.length && text[idx + 1] == delimiter) {
                idx += 2
                continue
            }
            return idx
        }
        idx++
    }
    return -1
}

/**
 * 剝除 Markdown 語法符號，供系統通知、單行預覽與純文字複製/分享使用。
 */
fun stripMarkdown(raw: String): String {
    if (raw.isBlank()) return ""
    val normalized = raw.replace("\r\n", "\n")
    // 先將表格轉為純文字「儲存格 / 列」，並移除對齊分隔行
    val tableProcessed = normalized.split("\n").mapNotNull { line ->
        val trimmed = line.trim()
        when {
            isTableAlignLine(trimmed) -> null
            trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.count { it == '|' } >= 2 -> {
                splitTableRow(trimmed).filter { it.isNotEmpty() }.joinToString(" / ")
            }
            else -> line
        }
    }.joinToString("\n")

    return tableProcessed
        .replace(Regex("```[a-zA-Z0-9_-]*\\n?"), "")
        .replace("```", "")
        .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s+"), "")
        .replace(Regex("(?m)^\\s*>\\s?"), "")
        .replace(Regex("(?m)^\\s*([-*_])(?:\\s*\\1){2,}\\s*$"), "")
        .replace(Regex("(?m)^\\s*[-*+]\\s+\\[[ xX]]\\s+"), "• ")
        .replace(Regex("(?m)^\\s*[-*+]\\s+"), "• ")
        .replace(Regex("!\\[([^\\]]*)]\\([^)]*\\)"), "$1")
        .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("~~(.+?)~~"), "$1")
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        .replace(Regex("__(.+?)__"), "$1")
        .replace(Regex("(?<!\\*)\\*(?!\\s)(.+?)(?<!\\s)\\*(?!\\*)"), "$1")
        .replace(Regex("(?<!_)_(?!\\s)(.+?)(?<!\\s)_(?!_)"), "$1")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}


