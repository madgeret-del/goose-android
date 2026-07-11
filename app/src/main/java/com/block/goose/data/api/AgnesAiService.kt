package com.block.goose.data.api

import android.util.Log
import com.block.goose.data.model.ChatSession
import com.block.goose.data.model.Message
import com.block.goose.data.model.MessageRole
import com.block.goose.data.model.SSEEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class AgnesMessage(
    val role: String,
    val content: String
)

@Serializable
data class AgnesChatRequest(
    val model: String,
    val messages: List<AgnesMessage>,
    val stream: Boolean = true
)

class AgnesAiService(
    private val baseUrl: String,
    private val apiKey: String,
    private val modelName: String = "agnes-2.0-flash"
) : AiService {

    private val TAG = "AgnesAiService"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun testConnection(): ApiResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/models")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Log.d(TAG, "Connection test successful")
                ApiResult.Success(true)
            } else {
                Log.e(TAG, "Connection test failed: ${response.code}")
                ApiResult.Error("HTTP ${response.code}: ${response.message}", response.code)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection test error", e)
            ApiResult.Error(e.message ?: "Connection failed")
        }
    }

    override suspend fun fetchSessions(): ApiResult<List<ChatSession>> {
        return ApiResult.Success(emptyList())
    }

    override suspend fun startNewSession(): ApiResult<String> {
        return ApiResult.Success(java.util.UUID.randomUUID().toString())
    }

    override suspend fun sendMessage(
        messages: List<Message>,
        sessionId: String
    ): Flow<SSEEvent> = flow {
        try {
            val agnesMessages = messages.map { msg ->
                AgnesMessage(
                    role = when (msg.role) {
                        MessageRole.USER -> "user"
                        MessageRole.ASSISTANT -> "assistant"
                        MessageRole.SYSTEM -> "system"
                    },
                    content = msg.content.joinToString(" ") {
                        when (it) {
                            is com.block.goose.data.model.MessageContent.Text -> it.text
                            else -> it.toString()
                        }
                    }
                )
            }

            val request = AgnesChatRequest(
                model = modelName,
                messages = agnesMessages,
                stream = true
            )

            val requestBodyJson = json.encodeToString(request)
            Log.d(TAG, "Request: $requestBodyJson")

            val httpRequest = Request.Builder()
                .url("$baseUrl/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(requestBodyJson.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(httpRequest).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Request failed: ${response.code} - $errorBody")
                throw IOException("HTTP ${response.code}: $errorBody")
            }

            val source = response.body?.source() ?: throw IOException("Empty response body")
            var accumulatedContent = ""

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ")
                    if (data == "[DONE]") break

                    try {
                        val event = parseAgnesEvent(data)
                        if (event != null) {
                            if (event is SSEEvent.MessageEvent) {
                                val textContent = event.message.content
                                    .filterIsInstance<com.block.goose.data.model.MessageContent.Text>()
                                    .joinToString("") { it.text }
                                accumulatedContent += textContent
                                emit(event)
                            } else {
                                emit(event)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse SSE event: $data", e)
                    }
                }
            }

            if (accumulatedContent.isNotEmpty()) {
                val finalMessage = Message.assistant(accumulatedContent)
                emit(SSEEvent.MessageEvent(message = finalMessage))
            }

            response.close()
            Log.d(TAG, "SSE stream completed")

        } catch (e: Exception) {
            Log.e(TAG, "sendMessage error", e)
            emit(SSEEvent.ErrorEvent(error = e.message ?: "Stream failed"))
        }
    }

    private fun parseAgnesEvent(data: String): SSEEvent? {
        return try {
            val jsonElement = json.parseToJsonElement(data)
            val choices = jsonElement.jsonObject["choices"]?.jsonArray ?: return null
            val choice = choices.firstOrNull()?.jsonObject ?: return null
            val delta = choice["delta"]?.jsonObject ?: return null
            val content = delta["content"]?.jsonPrimitive?.content
            if (content != null && content.isNotEmpty()) {
                SSEEvent.MessageEvent(
                    message = Message.assistant(content)
                )
            } else {
                val finishReason = choice["finish_reason"]?.jsonPrimitive?.content
                if (finishReason != null) {
                    SSEEvent.FinishEvent(reason = finishReason)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseAgnesEvent error", e)
            null
        }
    }
}
