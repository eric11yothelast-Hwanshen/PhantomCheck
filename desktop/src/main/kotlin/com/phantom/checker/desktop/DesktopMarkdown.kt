package com.phantom.checker.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * #468 桌機端輕量 Markdown 渲染器（支援標題 #/##/###、粗體、斜體、行內程式碼、無序/有序清單、程式碼塊、表格）。
 */
@Composable
fun DesktopMarkdownText(
    markdown: String,
    accentColor: Color,
    textColor: Color = DesktopPalette.TextPrimary,
    modifier: Modifier = Modifier
) {
    val blocks = parseMarkdownBlocks(markdown)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.titleMedium
                        2 -> MaterialTheme.typography.titleSmall
                        else -> MaterialTheme.typography.bodyMedium
                    }
                    Text(
                        text = renderInlineMarkdown(block.text, accentColor),
                        style = style,
                        fontWeight = FontWeight.Bold,
                        color = if (block.level <= 2) accentColor else textColor
                    )
                }
                is MdBlock.CodeBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.38f))
                            .border(1.dp, DesktopPalette.GlassBorder, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = block.code,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = DesktopPalette.TextSecondary
                        )
                    }
                }
                is MdBlock.Bullet -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(block.marker, style = MaterialTheme.typography.bodyMedium, color = accentColor, fontWeight = FontWeight.Bold)
                        Text(renderInlineMarkdown(block.text, accentColor), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                }
                is MdBlock.Table -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, DesktopPalette.GlassBorder, RoundedCornerShape(8.dp))
                    ) {
                        block.rows.forEachIndexed { rowIdx, cells ->
                            if (rowIdx > 0) HorizontalDivider(color = DesktopPalette.GlassBorder)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (rowIdx == 0) accentColor.copy(alpha = 0.14f) else Color.Transparent)
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                cells.forEach { cell ->
                                    Text(
                                        text = renderInlineMarkdown(cell, accentColor),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (rowIdx == 0) FontWeight.SemiBold else FontWeight.Normal,
                                        color = textColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
                is MdBlock.Paragraph -> {
                    Text(
                        text = renderInlineMarkdown(block.text, accentColor),
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor
                    )
                }
            }
        }
    }
}

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class CodeBlock(val code: String) : MdBlock
    data class Bullet(val marker: String, val text: String) : MdBlock
    data class Table(val rows: List<List<String>>) : MdBlock
    data class Paragraph(val text: String) : MdBlock
}

private fun parseMarkdownBlocks(raw: String): List<MdBlock> {
    val lines = raw.lines()
    val result = mutableListOf<MdBlock>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> i++
            trimmed.startsWith("```") -> {
                val sb = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append(lines[i])
                    i++
                }
                if (i < lines.size) i++
                result.add(MdBlock.CodeBlock(sb.toString()))
            }
            trimmed.startsWith("### ") -> {
                result.add(MdBlock.Heading(3, trimmed.removePrefix("### ").trim()))
                i++
            }
            trimmed.startsWith("## ") -> {
                result.add(MdBlock.Heading(2, trimmed.removePrefix("## ").trim()))
                i++
            }
            trimmed.startsWith("# ") -> {
                result.add(MdBlock.Heading(1, trimmed.removePrefix("# ").trim()))
                i++
            }
            trimmed.startsWith("|") && trimmed.endsWith("|") -> {
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                    val cells = lines[i].trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
                    val isSeparator = cells.all { c -> c.isNotEmpty() && c.all { it == '-' || it == ':' } }
                    if (!isSeparator) rows.add(cells)
                    i++
                }
                if (rows.isNotEmpty()) result.add(MdBlock.Table(rows))
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                result.add(MdBlock.Bullet("•", trimmed.drop(2).trim()))
                i++
            }
            Regex("^\\d+\\.\\s+.*").matches(trimmed) -> {
                val dotIdx = trimmed.indexOf('.')
                val marker = trimmed.substring(0, dotIdx + 1)
                result.add(MdBlock.Bullet(marker, trimmed.substring(dotIdx + 1).trim()))
                i++
            }
            else -> {
                result.add(MdBlock.Paragraph(trimmed))
                i++
            }
        }
    }
    return result
}

private fun renderInlineMarkdown(text: String, accentColor: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                } else {
                    append(text[i]); i++
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = accentColor, background = Color.Black.copy(alpha = 0.3f))) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i]); i++
                }
            }
            text[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    append(text[i]); i++
                }
            }
            else -> {
                append(text[i]); i++
            }
        }
    }
}
