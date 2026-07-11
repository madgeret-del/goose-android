package com.block.goose.data.api

import com.block.goose.data.model.ChatSession
import com.block.goose.data.model.Message
import com.block.goose.data.model.SSEEvent
import kotlinx.coroutines.flow.Flow

/**
 * Универсальный интерфейс для работы с AI-провайдерами.
 * Позволяет переключаться между Goose, Agnes AI и другими.
 */
interface AiService {
    
    /**
     * Проверка соединения с сервером.
     * @return ApiResult<Boolean> — успех или ошибка
     */
    suspend fun testConnection(): ApiResult<Boolean>
    
    /**
     * Получение списка сессий (если поддерживается).
     * @return ApiResult<List<ChatSession>> — список сессий или ошибка
     */
    suspend fun fetchSessions(): ApiResult<List<ChatSession>>
    
    /**
     * Создание новой сессии (диалога).
     * @return ApiResult<String> — ID сессии или ошибка
     */
    suspend fun startNewSession(): ApiResult<String>
    
    /**
     * Отправка сообщения и получение стримингового ответа.
     * @param messages — список сообщений (история диалога)
     * @param sessionId — ID сессии
     * @return Flow<SSEEvent> — поток событий в реальном времени
     */
    suspend fun sendMessage(
        messages: List<Message>,
        sessionId: String
    ): Flow<SSEEvent>
}

/**
 * Общий результат выполнения API-запроса.
 * @param T — тип данных при успешном ответе
 */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val code: Int? = null) : ApiResult<Nothing>()
}
