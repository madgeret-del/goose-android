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
import java.io.IOException
import java.util.concurrent.TimeUnit

class GooseApiService(
    private val settingsRepository: SettingsRepository
) {
    private val TAG = "GooseApiService"
    
    val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    val isTrialMode: Boolean
        get() = settingsRepository.baseUrl.contains("demo-goosed.fly.dev")
    
    private val baseUrl: String
        get() = settingsRepository.baseUrl
    
    private val secretKey: String
        get() = settingsRepository.secretKey
    
    private fun createRequest(path: String): Request.Builder {
        return Request.Builder()
            .url("${baseUrl}$path")
            .header("X-Secret-Key", secretKey)
    }
    
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
    
    suspend fun startAgent(workingDir: String = "."): ApiResult<AgentResponse> = withContext(Dispatchers.IO) {
        try {
            val bodyJson = """{"working_dir": "$workingDir"}"""
            val requestBody = bodyJson.toRequestBody("application/json".toMediaType())
            
            val request = createRequest("/agent/start")
                .post(requestBody)
                .build()
            
            Log.d(TAG, "Starting agent with working_dir: $workingDir")
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext ApiResult.Error("Empty response")
                Log.d(TAG, "Start agent response: $body")
                val agentResponse = json.decodeFromString<AgentResponse>(body)
                ApiResult.Success(agentResponse)
            } else {
                val errorBody = response.body?.string() ?: "No error details"
                Log.e(TAG, "Start agent failed: HTTP ${response.code}: $errorBody")
                ApiResult.Error("HTTP ${response.code}: $errorBody", response.code)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start agent", e)
            ApiResult.Error(e.message ?: "Failed to start agent")
        }
    }
    
    suspend fun resumeAgent(
        sessionId: String, 
        loadModelAndExtensions: Boolean = false
    ): ApiResult<SessionResponse> = withContext(Dispatchers.IO) {
        try {
            if (!loadModelAndExtensions) {
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
            } else {
                val bodyJson = """{"session_id": "$sessionId", "load_model_and_extensions": true}"""
                val requestBody = bodyJson.toRequestBody("application/json".toMediaType())
                
                val request = createRequest("/agent/resume")
                    .post(requestBody)
                    .build()
                
                Log.d(TAG, "Resuming agent with loadModelAndExtensions=true")
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext ApiResult.Error("Empty response")
                    Log.d(TAG, "Resume agent response: $body")
                    val sessionResponse = json.decodeFromString<SessionResponse>(body)
                    ApiResult.Success(sessionResponse)
                } else {
                    val errorBody = response.body?.string() ?: "No error details"
                    Log.e(TAG, "Resume agent failed: HTTP ${response.code}: $errorBody")
                    ApiResult.Error("HTTP ${response.code}: $errorBody", response.code)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume agent", e)
            ApiResult.Error(e.message ?: "Failed to resume agent")
        }
    }
    
    suspend fun updateFromSession(sessionId: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val bodyJson = """{"session_id": "$sessionId"}"""
            val requestBody = bodyJson.toRequestBody("application/json".toMediaType())
            
            val request = createRequest("/agent/update_from_session")
                .post(requestBody)
                .build()
            
            Log.d(TAG, "Updating from session: $sessionId")
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                Log.d(TAG, "Update from session successful")
                ApiResult.Success(Unit)
            } else {
                val errorBody = response.body?.string() ?: "No error details"
                Log.e(TAG, "Update from session failed: HTTP ${response.code}: $errorBody")
                ApiResult.Error("HTTP ${response.code}: $errorBody", response.code)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update from session", e)
            ApiResult.Error(e.message ?: "Failed to update from session")
        }
    }
    
    fun streamChat(
        messages: List<Message>,
        sessionId: String
    ): Flow<SSEEvent> = flow {
        val chatRequest = ChatRequest(
            messages = messages,
            sessionId = sessionId
        )
        
        val requestBody = json.encodeToString(ChatRequest.serializer(), chatRequest)
        Log.d(TAG, "Chat request body: $requestBody")
        
        val request = Request.Builder()
            .url("${baseUrl}/reply")
            .header("X-Secret-Key", secretKey)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        Log.d(TAG, "Starting SSE stream for session: $sessionId")
        
        val sseClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        
        val response = sseClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            Log.e(TAG, "SSE request failed: HTTP ${response.code}: $errorBody")
            throw IOException("HTTP ${response.code}: $errorBody")
        }
        
        val source = response.body?.source() ?: throw IOException("Empty response body")
        
        Log.d(TAG, "SSE connection established, reading events...")
        
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            
            if (line.startsWith("data: ")) {
                val eventData = line.removePrefix("data: ")
                if (eventData.isNotEmpty()) {
                    try {
                        Log.d(TAG, "SSE event data: $eventData")
                        val event = parseSSEEvent(eventData)
                        if (event != null) {
                            emit(event)
                            
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
        Log.d(TAG, "SSE stream closed")
    }.flowOn(Dispatchers.IO)
    
    private fun parseSSEEvent(data: String): SSEEvent? {
        return try {
            val typeRegex = """"type"\s*:\s*"([^"]+)"""".toRegex()
            val typeMatch = typeRegex.find(data)
            val type = typeMatch?.groupValues?.get(1)
            
            Log.d(TAG, "Parsing SSE event type: $type")
            
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
} events...")
        
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            
            if (line.startsWith("data: ")) {
                val eventData = line.removePrefix("data: ")
                if (eventData.isNotEmpty()) {
                    try {
                        Log.d(TAG, "SSE event data: $eventData")
                        val event = parseSSEEvent(eventData)
                        if (event != null) {
                            emit(event)
                            
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
        Log.d(TAG, "SSE stream closed")
    }.flowOn(Dispatchers.IO)
    
    private fun parseSSEEvent(data: String): SSEEvent? {
        return try {
            val typeRegex = """"type"\s*:\s*"([^"]+)"""".toRegex()
            val typeMatch = typeRegex.find(data)
            val type = typeMatch?.groupValues?.get(1)
            
            Log.d(TAG, "Parsing SSE event type: $type")
            
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
