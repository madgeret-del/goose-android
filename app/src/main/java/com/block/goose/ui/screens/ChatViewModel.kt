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
}        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSession = true, error = null) }
            when (val result = agnesAiService.startNewSession()) {
                is ApiResult.Success -> {
                    _uiState.update { 
                        it.copy(
                            currentSessionId = result.data,
                            isLoadingSession = false,
                            isSessionActivated = false
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
        // Agnes AI не поддерживает загрузку сессий, создаём новую
        Log.d(TAG, "loadSession called but Agnes AI doesn't support sessions, starting new")
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
                                isLoadingSession = false,
                                isSessionActivated = false
                            )
                        }
                        Log.d(TAG, "Created session: ${result.data}")
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
                // Для Agnes AI не нужна активация сессии, просто отправляем сообщение
                val allMessages = _uiState.value.messages
                Log.d(TAG, "Sending ${allMessages.size} messages to Agnes AI")

                agnesAiService.sendMessage(allMessages, sessionId)
                    .catch { e ->
                        Log.e(TAG, "Stream error", e)
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
                Log.e(TAG, "Error in sendMessageToSession", e)
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        isActivatingSession = false,
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
                        // Аккумулируем стриминговый текст
                        state.messages.toMutableList().apply {
                            val existingMessage = this[existingIndex]
                            this[existingIndex] = accumulateMessageContent(existingMessage, event.message)
                        }
                    } else {
                        // Добавляем новое сообщение
                        state.messages + event.message
                    }
                    state.copy(messages = newMessages)
                }
            }
            is SSEEvent.ErrorEvent -> {
                Log.e(TAG, "SSE Error: ${event.error}")
                _uiState.update { 
                    it.copy(error = event.error)
                }
            }
            is SSEEvent.FinishEvent -> {
                Log.d(TAG, "Stream finished: ${event.reason}")
                _uiState.update { it.copy(isLoading = false) }
            }
            is SSEEvent.UpdateConversationEvent -> {
                Log.d(TAG, "Updating conversation with ${event.conversation.size} messages")
                _uiState.update { 
                    it.copy(messages = event.conversation)
                }
            }
            is SSEEvent.ModelChangeEvent -> {
                Log.d(TAG, "Model changed: ${event.model}")
            }
            is SSEEvent.PingEvent -> {
                // Игнорируем
            }
        }
    }

    /**
     * Аккумулирует стриминговый текст — добавляет новый текст к существующему
     */
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
}            _uiState.update { it.copy(isLoadingSession = true, error = null) }
            
            when (val result = apiService.startAgent()) {
                is ApiResult.Success -> {
                    _uiState.update { 
                        it.copy(
                            currentSessionId = result.data.id,
                            messages = result.data.conversation ?: emptyList(),
                            isLoadingSession = false,
                            isSessionActivated = false
                        )
                    }
                    Log.d(TAG, "Started new session: ${result.data.id}")
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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSession = true, error = null) }
            
            when (val result = apiService.resumeAgent(sessionId, loadModelAndExtensions = false)) {
                is ApiResult.Success -> {
                    _uiState.update { 
                        it.copy(
                            currentSessionId = result.data.id,
                            messages = result.data.conversation ?: emptyList(),
                            isLoadingSession = false,
                            isSessionActivated = false
                        )
                    }
                    Log.d(TAG, "Loaded session: ${result.data.id}")
                }
                is ApiResult.Error -> {
                    _uiState.update { 
                        it.copy(
                            isLoadingSession = false,
                            error = result.message
                        )
                    }
                    Log.e(TAG, "Failed to load session: ${result.message}")
                }
            }
        }
    }
    
    fun sendMessage(text: String) {
        val trimmedText = text.trim()
        if (trimmedText.isEmpty() || _uiState.value.isLoading) return
        
        val sessionId = _uiState.value.currentSessionId
        
        if (sessionId == null) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoadingSession = true) }
                
                when (val result = apiService.startAgent()) {
                    is ApiResult.Success -> {
                        _uiState.update { 
                            it.copy(
                                currentSessionId = result.data.id,
                                isLoadingSession = false,
                                isSessionActivated = false
                            )
                        }
                        Log.d(TAG, "Created session: ${result.data.id}")
                        sendMessageToSession(trimmedText, result.data.id)
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
                // Activate session if needed (matches iOS flow)
                if (!_uiState.value.isSessionActivated) {
                    _uiState.update { it.copy(isActivatingSession = true) }
                    
                    Log.d(TAG, "Activating session: $sessionId")
                    
                    // Resume agent with model and extensions
                    when (val resumeResult = apiService.resumeAgent(sessionId, loadModelAndExtensions = true)) {
                        is ApiResult.Success -> {
                            Log.d(TAG, "Resume agent successful")
                        }
                        is ApiResult.Error -> {
                            Log.e(TAG, "Resume agent failed: ${resumeResult.message}")
                        }
                    }
                    
                    // Update from session (applies system prompt)
                    when (val updateResult = apiService.updateFromSession(sessionId)) {
                        is ApiResult.Success -> {
                            Log.d(TAG, "Update from session successful")
                        }
                        is ApiResult.Error -> {
                            Log.e(TAG, "Update from session failed: ${updateResult.message}")
                        }
                    }
                    
                    _uiState.update { 
                        it.copy(
                            isSessionActivated = true,
                            isActivatingSession = false
                        )
                    }
                }
                
                // Now stream the chat
                val allMessages = _uiState.value.messages
                Log.d(TAG, "Streaming chat with ${allMessages.size} messages")
                
                apiService.streamChat(allMessages, sessionId)
                    .catch { e ->
                        Log.e(TAG, "Stream error", e)
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
                Log.e(TAG, "Error in sendMessageToSession", e)
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        isActivatingSession = false,
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
                        // Accumulate streaming text content instead of replacing
                        state.messages.toMutableList().apply {
                            val existingMessage = this[existingIndex]
                            this[existingIndex] = accumulateMessageContent(existingMessage, event.message)
                        }
                    } else {
                        // Add new message
                        state.messages + event.message
                    }
                    state.copy(messages = newMessages)
                }
            }
            is SSEEvent.ErrorEvent -> {
                Log.e(TAG, "SSE Error: ${event.error}")
                _uiState.update { 
                    it.copy(error = event.error)
                }
            }
            is SSEEvent.FinishEvent -> {
                Log.d(TAG, "Stream finished: ${event.reason}")
                _uiState.update { it.copy(isLoading = false) }
            }
            is SSEEvent.UpdateConversationEvent -> {
                Log.d(TAG, "Updating conversation with ${event.conversation.size} messages")
                _uiState.update { 
                    it.copy(messages = event.conversation)
                }
            }
            is SSEEvent.ModelChangeEvent -> {
                Log.d(TAG, "Model changed: ${event.model}")
            }
            is SSEEvent.PingEvent -> {
                // Ignore ping events
            }
        }
    }
    
    /**
     * Accumulate streaming content - appends new text to existing text content
     */
    private fun accumulateMessageContent(existing: Message, incoming: Message): Message {
        // Get the current accumulated text from existing message
        val existingText = existing.content
            .filterIsInstance<MessageContent.Text>()
            .joinToString("") { it.text }
        
        // Get the new text chunk from incoming message
        val incomingText = incoming.content
            .filterIsInstance<MessageContent.Text>()
            .joinToString("") { it.text }
        
        // Combine: existing + new chunk
        val combinedText = existingText + incomingText
        
        // Build new content list - keep non-text content from incoming, add combined text
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
