package com.block.goose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.block.goose.data.model.Message
import com.block.goose.data.model.MessageContent
import com.block.goose.data.model.MessageRole
import com.block.goose.ui.theme.GooseColors

@Composable
fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER
    val bubbleColor = if (isUser) {
        GooseColors.userBubble()
    } else {
        GooseColors.assistantBubble()
    }
    val textColor = if (isUser) {
        GooseColors.userBubbleText()
    } else {
        GooseColors.assistantBubbleText()
    }
    
    val bubbleShape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = if (isUser) 20.dp else 6.dp,
        bottomEnd = if (isUser) 6.dp else 20.dp
    )
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(bubbleShape)
                .background(bubbleColor)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column {
                message.content.forEach { content ->
                    when (content) {
                        is MessageContent.Text -> {
                            if (content.text.isNotBlank()) {
                                Text(
                                    text = content.text,
                                    color = textColor,
                                    style = MaterialTheme.typography.bodyLarge,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                        is MessageContent.ToolRequest -> {
                            ToolRequestView(
                                toolCall = content.toolCall,
                                textColor = textColor
                            )
                        }
                        is MessageContent.ToolResponse -> {
                            ToolResponseView(
                                toolResult = content.toolResult,
                                textColor = textColor
                            )
                        }
                        is MessageContent.Thinking -> {
                            ThinkingView(
                                thinking = content.thinking,
                                textColor = textColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolRequestView(
    toolCall: com.block.goose.data.model.ToolCall,
    textColor: Color
) {
    val isDark = isSystemInDarkTheme()
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = GooseColors.toolBackground()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "🔧",
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = toolCall.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isDark) Color(0xFF64B5F6) else Color(0xFF1976D2),
                    fontFamily = FontFamily.Monospace
                )
            }
            if (toolCall.arguments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = toolCall.arguments.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    maxLines = 3
                )
            }
        }
    }
}

@Composable
private fun ToolResponseView(
    toolResult: com.block.goose.data.model.ToolResult,
    textColor: Color
) {
    val isDark = isSystemInDarkTheme()
    val statusColor = when (toolResult.status) {
        "success" -> GooseColors.Success
        "error" -> GooseColors.Error
        else -> GooseColors.Info
    }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = GooseColors.toolBackground()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (toolResult.status == "success") "✓" else "✗",
                    fontSize = 14.sp,
                    color = statusColor
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = toolResult.status.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor
                )
            }
            toolResult.error?.let { error ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = GooseColors.Error.copy(alpha = 0.9f),
                    maxLines = 3
                )
            }
        }
    }
}

@Composable
private fun ThinkingView(
    thinking: String,
    textColor: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = GooseColors.toolBackground()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "💭",
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Thinking...",
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor.copy(alpha = 0.7f)
                )
            }
            if (thinking.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = thinking,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.6f),
                    maxLines = 5
                )
            }
        }
    }
}
