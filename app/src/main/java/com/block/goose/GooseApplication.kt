package com.block.goose

import android.app.Application
import com.block.goose.data.api.AgnesAiService
import com.block.goose.data.api.GooseApiService
import com.block.goose.data.api.SettingsRepository

class GooseApplication : Application() {
    lateinit var settingsRepository: SettingsRepository
    private set
    lateinit var apiService: GooseApiService
    private set
    lateinit var agnesAiService: AgnesAiService  // ⬅️ НОВОЕ
    private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize repositories and services
        settingsRepository = SettingsRepository(this)
        apiService = GooseApiService(settingsRepository)
        
        // ⬅️ НОВОЕ: Инициализация Agnes AI
        agnesAiService = AgnesAiService(
            baseUrl = settingsRepository.baseUrl,
            apiKey = settingsRepository.secretKey
        )
    }

    companion object {
        lateinit var instance: GooseApplication
        private set
    }
}
