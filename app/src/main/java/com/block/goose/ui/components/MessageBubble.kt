package com.block.goose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.block.goose.data.model.Message
import com.block.goose.data.model.MessageContent
import com.block.goose.data.model.MessageRole

@Composable
fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (isUser) 20.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 20.dp
                    )
                )
                .background(
                    if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            message.content.forEach { content ->
                when (content) {
                    is MessageContent.Text -> {
                        if (content.text.isNotBlank()) {
                            Text(
                                text = content.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isUser) MaterialTheme.colorScheme.onPrimary
                                       else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    is MessageContent.ToolRequest -> {
                        ToolCallCard(
                            toolName = content.toolCall.name,
                            isCompleted = false,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    is MessageContent.ToolResponse -> {
                        ToolCallCard(
                            toolName = "Tool Response",
                            isCompleted = true,
                            isSuccess = content.toolResult.status == "success",
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    is MessageContent.Thinking -> {
                        // Show thinking indicator
                        Text(
                            text = "💭 Thinking...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ToolCallCard(
    toolName: String,
    isCompleted: Boolean,
    isSuccess: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (isCompleted) {
                if (isSuccess) "✓" else "✗"
            } else "⏳",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = toolName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}
