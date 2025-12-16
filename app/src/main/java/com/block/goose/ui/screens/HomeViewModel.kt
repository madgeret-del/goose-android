package com.block.goose.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.block.goose.GooseApplication
import com.block.goose.data.api.ApiResult
import com.block.goose.data.api.GooseApiService
import com.block.goose.data.api.SettingsRepository
import com.block.goose.data.model.ChatSession
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val sessions: List<ChatSession> = emptyList(),
    val isLoading: Boolean = false,
    val isTrialMode: Boolean = true,
    val error: String? = null
)

class HomeViewModel : ViewModel() {
    private val TAG = "HomeViewModel"
    
    private val apiService: GooseApiService = GooseApplication.instance.apiService
    private val settingsRepository: SettingsRepository = GooseApplication.instance.settingsRepository
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    init {
        // Observe settings changes and reload sessions when baseUrl changes
        viewModelScope.launch {
            settingsRepository.baseUrlFlow
                .distinctUntilChanged()
                .collect { baseUrl ->
                    val isTrialMode = baseUrl.contains("demo-goosed.fly.dev")
                    _uiState.update { it.copy(isTrialMode = isTrialMode) }
                    Log.d(TAG, "Base URL changed, reloading sessions. Trial mode: $isTrialMode")
                    loadSessions()
                }
        }
    }
    
    fun loadSessions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            when (val result = apiService.fetchSessions()) {
                is ApiResult.Success -> {
                    _uiState.update { 
                        it.copy(
                            sessions = result.data,
                            isLoading = false
                        )
                    }
                    Log.d(TAG, "Loaded ${result.data.size} sessions")
                }
                is ApiResult.Error -> {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = result.message
                        )
                    }
                    Log.e(TAG, "Failed to load sessions: ${result.message}")
                }
            }
        }
    }
    
    fun refresh() {
        loadSessions()
    }
}
