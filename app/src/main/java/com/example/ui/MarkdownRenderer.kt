package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val lines = markdown.lines()
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        var inCodeBlock = false
        val codeBlockContent = StringBuilder()
        
        for (line in lines) {
            val trimmed = line.trim()
            
            // Code block parsing
            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    // Render completed code block
                    CodeBlockCard(codeBlockContent.toString())
                    codeBlockContent.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                }
                continue
            }
            
            if (inCodeBlock) {
                codeBlockContent.append(line).append("\n")
                continue
            }
            
            // Header parsing
            if (trimmed.startsWith("# ")) {
                val headerText = trimmed.removePrefix("# ")
                Text(
                    text = parseMarkdownInline(headerText, primaryColor),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
                Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                continue
            }
            
            if (trimmed.startsWith("## ")) {
                val headerText = trimmed.removePrefix("## ")
                Text(
                    text = parseMarkdownInline(headerText, primaryColor),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                )
                continue
            }
            
            if (trimmed.startsWith("### ")) {
                val headerText = trimmed.removePrefix("### ")
                Text(
                    text = parseMarkdownInline(headerText, primaryColor),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                )
                continue
            }
            
            // Blockquote parsing
            if (trimmed.startsWith(">")) {
                val quoteText = trimmed.removePrefix(">").trim()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp, horizontal = 8.dp)
                        .background(
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.secondary,
                            shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = parseMarkdownInline(quoteText, primaryColor),
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                continue
            }
            
            // Bullet list parsing
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                val itemText = if (trimmed.startsWith("- ")) trimmed.removePrefix("- ") else trimmed.removePrefix("* ")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "• ",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = parseMarkdownInline(itemText, primaryColor),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                continue
            }
            
            // Plain paragraph text
            if (trimmed.isNotEmpty()) {
                Text(
                    text = parseMarkdownInline(line, primaryColor),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
        
        // Handle code block that didn't close
        if (inCodeBlock && codeBlockContent.isNotEmpty()) {
            CodeBlockCard(codeBlockContent.toString())
        }
    }
}

@Composable
fun CodeBlockCard(code: String) {
    val clipboardManager = LocalClipboardManager.current
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF232530)) // Sleek dark coding background
            .border(1.dp, Color(0xFF3E4050), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Code Block",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFA1A3B0),
                    fontFamily = FontFamily.Monospace
                )
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(code.trim()))
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = Color(0xFFA1A3B0),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = code.trim(),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                ),
                color = Color(0xFFF1F1F1)
            )
        }
    }
}

fun parseMarkdownInline(text: String, primaryColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            when {
                text.startsWith("**", index) -> {
                    val endToken = text.indexOf("**", index + 2)
                    if (endToken != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(index + 2, endToken))
                        }
                        index = endToken + 2
                    } else {
                        append("**")
                        index += 2
                    }
                }
                text.startsWith("*", index) -> {
                    val endToken = text.indexOf("*", index + 1)
                    if (endToken != -1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(index + 1, endToken))
                        }
                        index = endToken + 1
                    } else {
                        append("*")
                        index += 1
                    }
                }
                text.startsWith("`", index) -> {
                    val endToken = text.indexOf("`", index + 1)
                    if (endToken != -1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                color = primaryColor,
                                background = primaryColor.copy(alpha = 0.12f)
                            )
                        ) {
                            append(text.substring(index + 1, endToken))
                        }
                        index = endToken + 1
                    } else {
                        append("`")
                        index += 1
                    }
                }
                else -> {
                    append(text[index])
                    index++
                }
            }
        }
    }
}
