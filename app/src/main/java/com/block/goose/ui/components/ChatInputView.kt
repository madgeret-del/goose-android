package com.block.goose.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChatInputView(
    text: String = "",
    onTextChange: ((String) -> Unit)? = null,
    onSubmit: (() -> Unit)? = null,
    // New parameter names for compatibility
    value: String = text,
    onValueChange: ((String) -> Unit)? = onTextChange,
    onSend: (() -> Unit)? = onSubmit,
    onStop: (() -> Unit)? = null,
    isLoading: Boolean = false,
    showPlusButton: Boolean = false,
    placeholder: String = "I want to...",
    modifier: Modifier = Modifier
) {
    // Use whichever params are provided
    val actualText = if (onValueChange != null) value else text
    val actualOnChange = onValueChange ?: onTextChange ?: {}
    val actualOnSubmit = onSend ?: onSubmit ?: {}
    
    val focusRequester = remember { FocusRequester() }
    val canSubmit = actualText.isNotBlank()
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 12.dp, start = 16.dp, end = 12.dp)
        ) {
            // Text field
            BasicTextField(
                value = actualText,
                onValueChange = actualOnChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .padding(vertical = 8.dp),
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                maxLines = 4,
                decorationBox = { innerTextField ->
                    Box {
                        if (actualText.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )
            
            // Buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Plus button (optional)
                if (showPlusButton) {
                    IconButton(
                        onClick = { /* File attachment */ },
                        modifier = Modifier
                            .size(32.dp)
                            .border(
                                width = 0.5.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add attachment",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(32.dp))
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Send/Stop button
                IconButton(
                    onClick = {
                        if (isLoading && onStop != null) {
                            onStop()
                        } else if (canSubmit) {
                            actualOnSubmit()
                        }
                    },
                    enabled = isLoading || canSubmit,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isLoading -> MaterialTheme.colorScheme.error
                                canSubmit -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            }
                        )
                ) {
                    Icon(
                        imageVector = if (isLoading) Icons.Default.Stop else Icons.Default.ArrowUpward,
                        contentDescription = if (isLoading) "Stop" else "Send",
                        modifier = Modifier.size(18.dp),
                        tint = if (isLoading) Color.White else MaterialTheme.colorScheme.surface
                    )
                }
            }
        }
    }
}
