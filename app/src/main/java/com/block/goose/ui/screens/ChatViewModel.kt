package com.block.goose.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.block.goose.GooseApplication
import com.block.goose.data.api.ApiResult
import com.block.goose.data.api.GooseApiService
import com.block.goose.data.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingSession: Boolean = false,
    val currentSessionId: String? = null,
    val sessionName: String? = null,
    val error: String? = null
)

class ChatViewModel : ViewModel() {
    private val TAG = "ChatViewModel"
    
    private val apiService: GooseApiService = GooseApplication.instance.apiService
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    private var streamJob: Job? = null
    
    fun startNewSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSession = true, error = null) }
            
            when (val result = apiService.startAgent()) {
                is ApiResult.Success -> {
                    _uiState.update { 
                        it.copy(
                            currentSessionId = result.data.id,
                            messages = result.data.conversation ?: emptyList(),
                            isLoadingSession = false
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
            
            when (val result = apiService.resumeAgent(sessionId)) {
                is ApiResult.Success -> {
                    _uiState.update { 
                        it.copy(
                            currentSessionId = result.data.id,
                            messages = result.data.conversation ?: emptyList(),
                            isLoadingSession = false
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
        val sessionId = _uiState.value.currentSessionId
        
        if (sessionId == null) {
            // Start a new session first, then send
            viewModelScope.launch {
                when (val result = apiService.startAgent()) {
                    is ApiResult.Success -> {
                        _uiState.update { 
                            it.copy(currentSessionId = result.data.id)
                        }
                        sendMessageToSession(text, result.data.id)
                    }
                    is ApiResult.Error -> {
                        _uiState.update { 
                            it.copy(error = result.message)
                        }
                    }
                }
            }
        } else {
            sendMessageToSession(text, sessionId)
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
                
                apiService.streamChat(allMessages, sessionId)
                    .catch { e ->
                        Log.e(TAG, "Stream error", e)
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = e.message
                            )
                        }
                    }
                    .collect { event ->
                        handleSSEEvent(event)
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
                            this[existingIndex] = event.message
                        }
                    } else {
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
