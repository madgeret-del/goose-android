package com.block.goose.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.block.goose.GooseApplication
import com.block.goose.data.api.ApiResult
import com.block.goose.data.api.AiService
import com.block.goose.data.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingSession: Boolean = false,
    val isActivatingSession: Boolean = false,
    val currentSessionId: String? = null,
    val sessionName: String? = null,
    val isSessionActivated: Boolean = false,
    val error: String? = null
)

class ChatViewModel : ViewModel() {
    private val TAG = "ChatViewModel"
    private val agnesAiService: AiService = GooseApplication.instance.agnesAiService
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private var streamJob: Job? = null

    fun startNewSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSession = true, error = null) }
            when (val result = agnesAiService.startNewSession()) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            currentSessionId = result.data,
                            isLoadingSession = false
                        )
                    }
                    Log.d(TAG, "Started new session: ${result.data}")
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingSession = false,
                            error = result.message
                        )
                    }
                    Log.e(TAG, "Failed to start session: ${result.message}")
                }
            }
        }
    }

    fun loadSession(sessionId: String) {
        Log.d(TAG, "loadSession called, starting new session instead")
        startNewSession()
    }

    fun sendMessage(text: String) {
        val trimmedText = text.trim()
        if (trimmedText.isEmpty() || _uiState.value.isLoading) return

        val sessionId = _uiState.value.currentSessionId
        if (sessionId == null) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoadingSession = true) }
                when (val result = agnesAiService.startNewSession()) {
                    is ApiResult.Success -> {
                        _uiState.update {
                            it.copy(
                                currentSessionId = result.data,
                                isLoadingSession = false
                            )
                        }
                        sendMessageToSession(trimmedText, result.data)
                    }
                    is ApiResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoadingSession = false,
                                error = result.message
                            )
                        }
                    }
                }
            }
        } else {
            sendMessageToSession(trimmedText, sessionId)
        }
    }

    private fun sendMessageToSession(text: String, sessionId: String) {
        val userMessage = Message.user(text)
        _uiState.update { state ->
            state.copy(
                messages = state.messages + userMessage,
                isLoading = true,
                error = null
            )
        }

        streamJob = viewModelScope.launch {
            try {
                val allMessages = _uiState.value.messages
                agnesAiService.sendMessage(allMessages, sessionId)
                    .catch { e ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = e.message ?: "Stream failed"
                            )
                        }
                    }
                    .collect { event ->
                        handleSSEEvent(event)
                    }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to send message"
                    )
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun handleSSEEvent(event: SSEEvent) {
        when (event) {
            is SSEEvent.MessageEvent -> {
                _uiState.update { state ->
                    val existingIndex = state.messages.indexOfFirst { it.id == event.message.id }
                    val newMessages = if (existingIndex >= 0) {
                        state.messages.toMutableList().apply {
                            val existingMessage = this[existingIndex]
                            this[existingIndex] = accumulateMessageContent(existingMessage, event.message)
                        }
                    } else {
                        state.messages + event.message
                    }
                    state.copy(messages = newMessages)
                }
            }
            is SSEEvent.ErrorEvent -> {
                _uiState.update { it.copy(error = event.error) }
            }
            is SSEEvent.FinishEvent -> {
                _uiState.update { it.copy(isLoading = false) }
            }
            is SSEEvent.UpdateConversationEvent -> {
                _uiState.update { it.copy(messages = event.conversation) }
            }
            else -> {
                // Ignore other event types
            }
        }
    }

    private fun accumulateMessageContent(existing: Message, incoming: Message): Message {
        val existingText = existing.content
            .filterIsInstance<MessageContent.Text>()
            .joinToString("") { it.text }

        val incomingText = incoming.content
            .filterIsInstance<MessageContent.Text>()
            .joinToString("") { it.text }

        val combinedText = existingText + incomingText

        val nonTextContent = incoming.content.filter { it !is MessageContent.Text }
        val newContent = if (combinedText.isNotEmpty()) {
            listOf(MessageContent.Text(text = combinedText)) + nonTextContent
        } else {
            nonTextContent
        }

        return incoming.copy(content = newContent)
    }

    fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
        _uiState.update { it.copy(isLoading = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        streamJob?.cancel()
    }
}
