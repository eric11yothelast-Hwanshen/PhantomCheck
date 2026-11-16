package com.phantom.checker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 輕量 Markdown 渲染元件（支援粗體、行內代碼、清單、標題與程式碼區塊）。
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
                        text = parseInlineMarkdown(block.text, inlineCodeBg),
                        style = headingStyle
                    )
                }
                is MdBlock.Bullet -> {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = block.marker,
                            style = style.copy(color = color, fontWeight = FontWeight.Bold)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = parseInlineMarkdown(block.text, inlineCodeBg),
                            style = style.copy(color = color),
                            modifier = Modifier.weight(1f)
                        )
                    }
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
                        text = parseInlineMarkdown(block.text, inlineCodeBg),
                        style = style.copy(color = color)
                    )
                }
            }
        }
    }
}

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val marker: String, val text: String) : MdBlock
    data class CodeBlock(val code: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
}

private fun parseMarkdownBlocks(raw: String): List<MdBlock> {
    val lines = raw.replace("\r\n", "\n").split("\n")
    val result = mutableListOf<MdBlock>()
    var inCode = false
    val codeBuf = StringBuilder()

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            if (inCode) {
                result.add(MdBlock.CodeBlock(codeBuf.toString().trimEnd()))
                codeBuf.clear()
                inCode = false
            } else {
                inCode = true
            }
            continue
        }
        if (inCode) {
            codeBuf.appendLine(line)
            continue
        }
        if (trimmed.isEmpty()) continue

        when {
            trimmed.startsWith("### ") -> result.add(MdBlock.Heading(3, trimmed.removePrefix("### ").trim()))
            trimmed.startsWith("## ") -> result.add(MdBlock.Heading(2, trimmed.removePrefix("## ").trim()))
            trimmed.startsWith("# ") -> result.add(MdBlock.Heading(1, trimmed.removePrefix("# ").trim()))
            trimmed.startsWith("- ") -> result.add(MdBlock.Bullet("•", trimmed.removePrefix("- ").trim()))
            trimmed.startsWith("* ") -> result.add(MdBlock.Bullet("•", trimmed.removePrefix("* ").trim()))
            trimmed.startsWith("• ") -> result.add(MdBlock.Bullet("•", trimmed.removePrefix("• ").trim()))
            else -> {
                val numMatch = Regex("^(\\d+\\.)\\s+(.*)$").matchEntire(trimmed)
                if (numMatch != null) {
                    result.add(MdBlock.Bullet(numMatch.groupValues[1], numMatch.groupValues[2]))
                } else {
                    result.add(MdBlock.Paragraph(trimmed))
                }
            }
        }
    }
    if (inCode && codeBuf.isNotEmpty()) {
        result.add(MdBlock.CodeBlock(codeBuf.toString().trimEnd()))
    }
    return result
}

private fun parseInlineMarkdown(text: String, codeBg: Color): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
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
            append(text[i])
            i++
        }
    }
}

/**
 * 剝除 Markdown 語法符號，供系統通知、單行預覽與純文字複製/分享使用。
 */
fun stripMarkdown(raw: String): String {
    if (raw.isBlank()) return ""
    return raw
        .replace("\r\n", "\n")
        .replace(Regex("```[a-zA-Z0-9_-]*\\n?"), "")
        .replace("```", "")
        .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s+"), "")
        .replace(Regex("(?m)^\\s*([-*_])\\1{2,}\\s*$"), "")
        .replace(Regex("(?m)^\\s*[-*+]\\s+"), "• ")
        .replace(Regex("!\\[([^\\]]*)]\\([^)]*\\)"), "$1")
        .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        .replace(Regex("__(.+?)__"), "$1")
        .replace(Regex("(?<!\\*)\\*(?!\\s)(.+?)(?<!\\s)\\*(?!\\*)"), "$1")
        .replace(Regex("(?<!_)_(?!\\s)(.+?)(?<!\\s)_(?!_)"), "$1")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

