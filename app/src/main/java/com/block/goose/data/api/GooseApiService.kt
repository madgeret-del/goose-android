package com.block.goose.data.api

import android.util.Log
import com.block.goose.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val code: Int? = null) : ApiResult<Nothing>()
}

class GooseApiService(
    private val settingsRepository: SettingsRepository
) {
    private val TAG = "GooseApiService"
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
        encodeDefaults = true
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    val isTrialMode: Boolean
        get() = settingsRepository.baseUrl.contains("demo-goosed.fly.dev")
    
    private fun createRequest(path: String): Request.Builder {
        return Request.Builder()
            .url("${settingsRepository.baseUrl}$path")
            .header("X-Secret-Key", settingsRepository.secretKey)
    }
    
    // Test connection
    suspend fun testConnection(): ApiResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val request = createRequest("/status").get().build()
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                ApiResult.Success(true)
            } else {
                ApiResult.Error("HTTP ${response.code}: ${response.message}", response.code)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed", e)
            ApiResult.Error(e.message ?: "Connection failed")
        }
    }
    
    // Fetch sessions
    suspend fun fetchSessions(): ApiResult<List<ChatSession>> = withContext(Dispatchers.IO) {
        if (isTrialMode) {
            return@withContext ApiResult.Success(emptyList())
        }
        
        try {
            val request = createRequest("/sessions").get().build()
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext ApiResult.Error("Empty response")
                val sessionsResponse = json.decodeFromString<SessionsResponse>(body)
                ApiResult.Success(sessionsResponse.sessions)
            } else {
                ApiResult.Error("HTTP ${response.code}", response.code)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch sessions", e)
            ApiResult.Error(e.message ?: "Failed to fetch sessions")
        }
    }
    
    // Start new agent session
    suspend fun startAgent(workingDir: String = "."): ApiResult<AgentResponse> = withContext(Dispatchers.IO) {
        try {
            val bodyJson = """{"working_dir": "$workingDir"}"""
            val requestBody = bodyJson.toRequestBody("application/json".toMediaType())
            
            val request = createRequest("/agent/start")
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext ApiResult.Error("Empty response")
                val agentResponse = json.decodeFromString<AgentResponse>(body)
                ApiResult.Success(agentResponse)
            } else {
                val errorBody = response.body?.string() ?: "No error details"
                ApiResult.Error("HTTP ${response.code}: $errorBody", response.code)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start agent", e)
            ApiResult.Error(e.message ?: "Failed to start agent")
        }
    }
    
    // Resume agent session
    suspend fun resumeAgent(sessionId: String): ApiResult<SessionResponse> = withContext(Dispatchers.IO) {
        try {
            val request = createRequest("/sessions/$sessionId").get().build()
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext ApiResult.Error("Empty response")
                val sessionResponse = json.decodeFromString<SessionResponse>(body)
                ApiResult.Success(sessionResponse)
            } else {
                val errorBody = response.body?.string() ?: "No error details"
                ApiResult.Error("HTTP ${response.code}: $errorBody", response.code)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume agent", e)
            ApiResult.Error(e.message ?: "Failed to resume agent")
        }
    }
    
    // Stream chat with SSE
    fun streamChat(
        messages: List<Message>,
        sessionId: String
    ): Flow<SSEEvent> = flow {
        val chatRequest = ChatRequest(
            messages = messages,
            sessionId = sessionId
        )
        
        val requestBody = json.encodeToString(ChatRequest.serializer(), chatRequest)
            .toRequestBody("application/json".toMediaType())
        
        val request = createRequest("/reply")
            .post(requestBody)
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .build()
        
        Log.d(TAG, "Starting SSE stream for session: $sessionId")
        
        val sseClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // No timeout for SSE
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        
        val response = sseClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code}: ${response.body?.string()}")
        }
        
        val source = response.body?.source() ?: throw IOException("Empty response body")
        
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            
            if (line.startsWith("data: ")) {
                val eventData = line.removePrefix("data: ")
                if (eventData.isNotEmpty()) {
                    try {
                        // Parse the SSE event based on type field
                        val event = parseSSEEvent(eventData)
                        if (event != null) {
                            emit(event)
                            
                            // Check for finish event
                            if (event is SSEEvent.FinishEvent) {
                                Log.d(TAG, "Stream finished: ${event.reason}")
                                break
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse SSE event: $eventData", e)
                    }
                }
            }
        }
        
        response.close()
    }.flowOn(Dispatchers.IO)
    
    private fun parseSSEEvent(data: String): SSEEvent? {
        return try {
            // First, determine the event type
            val typeRegex = """"type"\s*:\s*"([^"]+)"""".toRegex()
            val typeMatch = typeRegex.find(data)
            val type = typeMatch?.groupValues?.get(1)
            
            when (type) {
                "Message" -> json.decodeFromString<SSEEvent.MessageEvent>(data)
                "Error" -> json.decodeFromString<SSEEvent.ErrorEvent>(data)
                "Finish" -> json.decodeFromString<SSEEvent.FinishEvent>(data)
                "ModelChange" -> json.decodeFromString<SSEEvent.ModelChangeEvent>(data)
                "Ping" -> json.decodeFromString<SSEEvent.PingEvent>(data)
                "UpdateConversation" -> json.decodeFromString<SSEEvent.UpdateConversationEvent>(data)
                else -> {
                    Log.w(TAG, "Unknown SSE event type: $type")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SSE parse error for: $data", e)
            null
        }
    }
}
