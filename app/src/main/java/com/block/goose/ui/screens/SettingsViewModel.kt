package com.block.goose.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.block.goose.GooseApplication
import com.block.goose.data.api.ApiResult
import com.block.goose.data.api.GooseApiService
import com.block.goose.data.api.SettingsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val baseUrl: String = SettingsRepository.DEFAULT_BASE_URL,
    val secretKey: String = SettingsRepository.DEFAULT_SECRET_KEY,
    val isConnected: Boolean = false,
    val isTesting: Boolean = false,
    val connectionError: String? = null
)

class SettingsViewModel : ViewModel() {
    private val TAG = "SettingsViewModel"
    
    private val apiService: GooseApiService = GooseApplication.instance.apiService
    private val settingsRepository: SettingsRepository = GooseApplication.instance.settingsRepository
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        loadSettings()
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            combine(
                settingsRepository.baseUrlFlow,
                settingsRepository.secretKeyFlow
            ) { baseUrl, secretKey ->
                _uiState.update {
                    it.copy(baseUrl = baseUrl, secretKey = secretKey)
                }
            }.collect()
        }
    }
    
    fun updateBaseUrl(url: String) {
        _uiState.update { it.copy(baseUrl = url) }
    }
    
    fun updateSecretKey(key: String) {
        _uiState.update { it.copy(secretKey = key) }
    }
    
    fun saveSettings() {
        viewModelScope.launch {
            settingsRepository.saveSettings(
                baseUrl = _uiState.value.baseUrl,
                secretKey = _uiState.value.secretKey
            )
            Log.d(TAG, "Settings saved")
        }
    }
    
    fun testConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, connectionError = null) }
            
            // Temporarily save settings for testing
            settingsRepository.saveSettings(
                baseUrl = _uiState.value.baseUrl,
                secretKey = _uiState.value.secretKey
            )
            
            when (val result = apiService.testConnection()) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isConnected = true,
                            isTesting = false,
                            connectionError = null
                        )
                    }
                    Log.d(TAG, "Connection test successful")
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isConnected = false,
                            isTesting = false,
                            connectionError = result.message
                        )
                    }
                    Log.e(TAG, "Connection test failed: ${result.message}")
                }
            }
        }
    }
    
    fun resetToTrialMode() {
        viewModelScope.launch {
            settingsRepository.resetToTrialMode()
            _uiState.update {
                it.copy(
                    baseUrl = SettingsRepository.DEFAULT_BASE_URL,
                    secretKey = SettingsRepository.DEFAULT_SECRET_KEY,
                    isConnected = false,
                    connectionError = null
                )
            }
            Log.d(TAG, "Reset to trial mode")
        }
    }
}
