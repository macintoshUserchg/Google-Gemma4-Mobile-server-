/*
 * Copyright 2025 Rigrise
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rigrise.gallery.customtasks.modelserver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rigrise.gallery.MainActivity
import com.rigrise.gallery.R
import com.rigrise.gallery.data.Model
import com.rigrise.gallery.runtime.LlmModelHelper
import com.rigrise.gallery.runtime.runtimeHelper
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.ApplicationCall
import io.ktor.util.pipeline.PipelineContext
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.cio.CIO
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "OpenAiServer"
private const val NOTIFICATION_ID = 1001
private const val CHANNEL_ID = "model_server_channel"
private const val EXPECTED_API_KEY = "mobile@123"

/**
 * Ktor HTTP server that provides OpenAI-compatible endpoints.
 */
class OpenAiServer(
  private val context: Context,
) {
  // Whether to require an API key for requests
  var requireApiKey: Boolean = true

  // Coroutine scope for running inference
  private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  // Mutex to ensure only one inference runs at a time
  private val inferenceMutex = Mutex()

  private var engine: ApplicationEngine? = null
  
  private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  private var _port = MutableStateFlow(0)
  val port: StateFlow<Int> = _port

  private var _isRunning = MutableStateFlow(false)
  val isRunning: StateFlow<Boolean> = _isRunning

  private var _requestCount = MutableStateFlow(0)
  val requestCount: StateFlow<Int> = _requestCount

  private var _lastError = MutableStateFlow<String?>(null)
  val lastError: StateFlow<String?> = _lastError

  private var activeModel: Model? = null

  private var shouldStop = false

  /**
   * Start the server on the specified port.
   */
  fun start(
    port: Int = 8080,
    model: Model,
    requireApiKey: Boolean = true,
    onStarted: (String) -> Unit,
    onError: (String) -> Unit,
  ) {
    if (_isRunning.value) {
      onError("Server is already running")
      return
    }

    if (model.instance == null) {
      onError("Model is not initialized. Please wait for initialization to complete.")
      return
    }

    this.activeModel = model
    this.requireApiKey = requireApiKey
    shouldStop = false

    try {
      val jsonParser = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
      }
      
      engine = embeddedServer(CIO, port = port) {
        install(ContentNegotiation) {
          json(jsonParser)
        }
        
        routing {
          // Helper to validate API key
          suspend fun PipelineContext<Unit, ApplicationCall>.validateApiKey(): Boolean {
            if (!this@OpenAiServer.requireApiKey) return true

            val authHeader = call.request.header("Authorization")
            if (authHeader != "Bearer $EXPECTED_API_KEY") {
              val errorResponse = createErrorResponse("Invalid API Key", "invalid_request_error")
              call.respondText(errorResponse, ContentType.Application.Json, HttpStatusCode.Unauthorized)
              return false
            }
            return true
          }

          get("/health") {
            if (!validateApiKey()) return@get
            call.respondText(
              """{"status": "ok", "model": "${activeModel?.name ?: "none"}"}""", 
              ContentType.Application.Json, 
              HttpStatusCode.OK
            )
          }
          
          get("/v1/models") {
            if (!validateApiKey()) return@get

            _requestCount.value += 1
            val modelName = activeModel?.name ?: "none"
            val modelInfo = ModelInfo(
                id = modelName,
                created = System.currentTimeMillis() / 1000
            )
            val responseObj = ModelsListResponse(data = listOf(modelInfo))
            val responseJson = jsonParser.encodeToString(responseObj)
            call.respondText(responseJson, ContentType.Application.Json, HttpStatusCode.OK)
          }

          get("/api/tags") {
            if (!validateApiKey()) return@get
            // Ollama-compatible tags endpoint
            _requestCount.value += 1
            val modelName = activeModel?.name ?: "none"
            val modelSize = activeModel?.totalBytes ?: 0L
            
            // Format time as ISO 8601
            val df = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            df.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val timestamp = df.format(java.util.Date())

            val modelInfo = OllamaModel(
              name = modelName,
              model = modelName,
              modified_at = timestamp,
              size = modelSize,
              details = OllamaModelDetails(
                family = if (modelName.lowercase().contains("gemma")) "gemma" else "unknown",
                parameter_size = if (modelName.contains("2b")) "2B" else if (modelName.contains("7b")) "7B" else "unknown"
              )
            )
            val responseObj = OllamaTagsResponse(models = listOf(modelInfo))
            val responseJson = jsonParser.encodeToString(responseObj)
            call.respondText(responseJson, ContentType.Application.Json, HttpStatusCode.OK)
          }

          post("/api/chat") {
            if (!validateApiKey()) return@post
            // Ollama-compatible chat endpoint
            _requestCount.value += 1
            val requestBody = call.receiveText()
            try {
              val request = jsonParser.decodeFromString<OllamaChatRequest>(requestBody)
              val modelName = activeModel?.name ?: "unknown"
              val lastMessage = request.messages.lastOrNull { it.role == "user" }
              val userMessage = lastMessage?.textContent ?: ""
              
              val startTime = System.currentTimeMillis()
              var ttft = 0L
              val df = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
              df.timeZone = java.util.TimeZone.getTimeZone("UTC")

              if (request.stream == true) {
                call.respondTextWriter(contentType = ContentType.Application.Json, status = HttpStatusCode.OK) {
                  val writer = this
                  inferenceMutex.withLock {
                    var lastFullResult = ""
                    activeModel!!.runtimeHelper.generateResponseStream(
                      model = activeModel!!,
                      input = userMessage,
                    ).collect { partialResult ->
                      if (ttft == 0L && partialResult.isNotEmpty()) ttft = System.currentTimeMillis() - startTime
                      if (partialResult.isNotEmpty() && partialResult != lastFullResult) {
                        val newChunk = if (partialResult.startsWith(lastFullResult)) {
                          partialResult.substring(lastFullResult.length)
                        } else partialResult
                        
                        if (newChunk.isNotEmpty()) {
                          val chunk = OllamaChatResponse(
                            model = modelName,
                            created_at = df.format(java.util.Date()),
                            message = ChatMessage(role = "assistant", content = JsonPrimitive(newChunk)),
                            done = false
                          )
                          writer.write(jsonParser.encodeToString(chunk) + "\n")
                          writer.flush()
                        }
                        lastFullResult = partialResult
                      }
                    }
                    val finalResp = OllamaChatResponse(
                      model = modelName,
                      created_at = df.format(java.util.Date()),
                      done = true,
                      total_duration = (System.currentTimeMillis() - startTime) * 1_000_000
                    )
                    writer.write(jsonParser.encodeToString(finalResp) + "\n")
                    writer.flush()
                  }
                }
              } else {
                val responseText = inferenceMutex.withLock {
                  runInferenceBlocking(activeModel!!, userMessage) {
                    if (ttft == 0L) ttft = System.currentTimeMillis() - startTime
                  }
                }
                val response = OllamaChatResponse(
                  model = modelName,
                  created_at = df.format(java.util.Date()),
                  message = ChatMessage(role = "assistant", content = JsonPrimitive(responseText)),
                  done = true,
                  total_duration = (System.currentTimeMillis() - startTime) * 1_000_000
                )
                call.respondText(jsonParser.encodeToString(response), ContentType.Application.Json)
              }
            } catch (e: Exception) {
              call.respondText("{\"error\": \"${e.message}\"}", ContentType.Application.Json, HttpStatusCode.InternalServerError)
            }
          }

          post("/api/generate") {
            if (!validateApiKey()) return@post
            // Ollama-compatible generate endpoint
            _requestCount.value += 1
            val requestBody = call.receiveText()
            try {
              val request = jsonParser.decodeFromString<OllamaGenerateRequest>(requestBody)
              val modelName = activeModel?.name ?: "unknown"
              val userMessage = request.prompt
              
              val startTime = System.currentTimeMillis()
              var ttft = 0L
              val df = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
              df.timeZone = java.util.TimeZone.getTimeZone("UTC")

              if (request.stream == true) {
                call.respondTextWriter(contentType = ContentType.Application.Json, status = HttpStatusCode.OK) {
                  val writer = this
                  inferenceMutex.withLock {
                    var lastFullResult = ""
                    activeModel!!.runtimeHelper.generateResponseStream(
                      model = activeModel!!,
                      input = userMessage,
                    ).collect { partialResult ->
                      if (ttft == 0L && partialResult.isNotEmpty()) ttft = System.currentTimeMillis() - startTime
                      if (partialResult.isNotEmpty() && partialResult != lastFullResult) {
                        val newChunk = if (partialResult.startsWith(lastFullResult)) {
                          partialResult.substring(lastFullResult.length)
                        } else partialResult
                        
                        if (newChunk.isNotEmpty()) {
                          val chunk = OllamaGenerateResponse(
                            model = modelName,
                            created_at = df.format(java.util.Date()),
                            response = newChunk,
                            done = false
                          )
                          writer.write(jsonParser.encodeToString(chunk) + "\n")
                          writer.flush()
                        }
                        lastFullResult = partialResult
                      }
                    }
                    val finalResp = OllamaGenerateResponse(
                      model = modelName,
                      created_at = df.format(java.util.Date()),
                      response = "",
                      done = true,
                      total_duration = (System.currentTimeMillis() - startTime) * 1_000_000
                    )
                    writer.write(jsonParser.encodeToString(finalResp) + "\n")
                    writer.flush()
                  }
                }
              } else {
                val responseText = inferenceMutex.withLock {
                  runInferenceBlocking(activeModel!!, userMessage) {
                    if (ttft == 0L) ttft = System.currentTimeMillis() - startTime
                  }
                }
                val response = OllamaGenerateResponse(
                  model = modelName,
                  created_at = df.format(java.util.Date()),
                  response = responseText,
                  done = true,
                  total_duration = (System.currentTimeMillis() - startTime) * 1_000_000
                )
                call.respondText(jsonParser.encodeToString(response), ContentType.Application.Json)
              }
            } catch (e: Exception) {
              call.respondText("{\"error\": \"${e.message}\"}", ContentType.Application.Json, HttpStatusCode.InternalServerError)
            }
          }
          
          post("/v1/chat/completions") {
            if (!validateApiKey()) return@post

            _requestCount.value += 1
            val requestBody = call.receiveText()
            Log.d(TAG, "Request body: $requestBody")
            
            try {
              val request = jsonParser.decodeFromString<ChatCompletionRequest>(requestBody)

              // Extract user message and images
              val lastMessage = request.messages.lastOrNull { it.role == "user" }
              val userMessage = lastMessage?.textContent ?: "No input"
              
              // In the original, imageUrls was seemingly an extension or property that we'll handle gracefully:
              // Since it might not exist if we don't have the full context, we default to empty if unable to extract.
              val imageUrls: List<String> = try {
                 // Try to access it safely if it exists via reflection or property call
                 (lastMessage as? Any)?.let { msg ->
                    msg.javaClass.methods.find { it.name == "getImageUrls" }?.invoke(msg) as? List<String>
                 } ?: emptyList()
              } catch (e: Exception) {
                 emptyList()
              }

              // Use requested model if provided, otherwise use active model
              val modelName = request.model ?: activeModel?.name ?: "unknown"

              Log.d(TAG, "Running inference for TEST/API with model: $modelName, input: $userMessage")
              val startTime = System.currentTimeMillis()
              var ttft = 0L

              val bitmaps = imageUrls.mapNotNull { url ->
                if (url.startsWith("data:image")) {
                  val base64Data = url.substringAfter("base64,")
                  decodeBase64ToBitmap(base64Data)
                } else null
              }

              if (request.stream == true) {
                call.response.header("Cache-Control", "no-cache")
                call.response.header("Connection", "keep-alive")
                call.respondTextWriter(contentType = ContentType.Text.EventStream, status = HttpStatusCode.OK) {
                  val writer = this
                  val requestId = "chatcmpl-${System.currentTimeMillis()}"
                  var clientDisconnected = false
                  
                  inferenceMutex.withLock {
                    var lastFullResult = ""
                    try {
                      activeModel!!.runtimeHelper.generateResponseStream(
                        model = activeModel!!,
                        input = userMessage,
                        images = bitmaps,
                        audioClips = emptyList(),
                      ).collect { partialResult ->
                        if (clientDisconnected || shouldStop) {
                           throw kotlinx.coroutines.CancellationException("Client disconnected or server stopped")
                        }

                        if (ttft == 0L && partialResult.isNotEmpty()) ttft = System.currentTimeMillis() - startTime
                        
                        if (partialResult.isNotEmpty() && partialResult != lastFullResult) {
                          val newChunk = if (partialResult.startsWith(lastFullResult)) {
                            partialResult.substring(lastFullResult.length)
                          } else {
                            partialResult
                          }
                          
                          if (newChunk.isNotEmpty()) {
                            val chunkResponse = ChatCompletionChunk(
                              id = requestId,
                              created = System.currentTimeMillis() / 1000,
                              model = modelName,
                              choices = listOf(
                                DeltaChoice(
                                  index = 0,
                                  delta = DeltaContent(content = newChunk)
                                )
                              )
                            )
                            val data = "data: ${jsonParser.encodeToString(chunkResponse)}\n\n"
                            try {
                                writer.write(data)
                                writer.flush()
                            } catch (e: Exception) {
                                Log.e(TAG, "Client disconnected during streaming chunk", e)
                                clientDisconnected = true
                            }
                          }
                          lastFullResult = partialResult
                        }
                      }
                      
                      if (!clientDisconnected) {
                          try {
                              writer.write("data: [DONE]\n\n")
                              writer.flush()
                          } catch (e: Exception) {
                              Log.e(TAG, "Client disconnected on final chunk", e)
                          }
                          
                          val latency = System.currentTimeMillis() - startTime
                          coroutineScope.launch {
                              ModelServerLogManager.addLog(ModelServerLog(
                                  method = "POST",
                                  path = "/v1/chat/completions",
                                  requestBody = requestBody,
                                  responseBody = lastFullResult,
                                  latencyMs = latency,
                                  ttftMs = ttft,
                                  tps = if (latency > 0) (lastFullResult.length / 4.0) / (latency / 1000.0) else 0.0
                              ))
                          }
                      }
                    } catch (e: Exception) {
                        if (e !is kotlinx.coroutines.CancellationException && !clientDisconnected) {
                            try {
                                writer.write("data: {\"error\": {\"message\": \"${e.message}\"}}\n\n")
                                writer.flush()
                            } catch (writeEx: Exception) {
                                Log.e(TAG, "Client disconnected during error reporting", writeEx)
                            }
                        }
                    }
                  }
                }
              } else {
                // Run inference with timeout and synchronization
                val responseText = inferenceMutex.withLock {
                  withTimeoutOrNull(300000L) { // 5 minutes for slow models
                    runInferenceBlocking(activeModel!!, userMessage, bitmaps) {
                        if (ttft == 0L) ttft = System.currentTimeMillis() - startTime
                    }
                  }
                } ?: "Inference timeout"

                val endTime = System.currentTimeMillis()
                val latency = endTime - startTime
                Log.d(TAG, "Test/API Inference result: $responseText")

                // Build OpenAI-compatible response
                val response = ChatCompletionResponse(
                  id = "chatcmpl-${System.currentTimeMillis()}",
                  created = System.currentTimeMillis() / 1000,
                  model = modelName,
                  choices = listOf(
                    Choice(
                      index = 0,
                      message = ChatMessage(role = "assistant", content = JsonPrimitive(responseText)),
                      finishReason = "stop",
                    )
                  ),
                )

                val responseJson = jsonParser.encodeToString(response)
                call.respondText(responseJson, ContentType.Application.Json, HttpStatusCode.OK)
                
                // Log to inspector
                coroutineScope.launch {
                    ModelServerLogManager.addLog(ModelServerLog(
                        method = "POST",
                        path = "/v1/chat/completions",
                        requestBody = requestBody,
                        responseBody = responseJson,
                        latencyMs = latency,
                        ttftMs = ttft,
                        tps = if (latency > 0) (responseText.length / 4.0) / (latency / 1000.0) else 0.0
                    ))
                }
              }
            } catch (e: Exception) {
              Log.e(TAG, "Error parsing request or running inference", e)
              val errorResponse = createErrorResponse(e.message ?: "Inference failed", "server_error")
              call.respondText(errorResponse, ContentType.Application.Json, HttpStatusCode.InternalServerError)
              coroutineScope.launch {
                  ModelServerLogManager.addLog(ModelServerLog(
                      method = "POST", path = "/v1/chat/completions", requestBody = requestBody, responseBody = errorResponse, isError = true
                  ))
              }
            }
          }

          // MCP (Model Context Protocol) routes
          mcpRoutes(
            getModelName = { activeModel?.name ?: "unknown" },
            runInference = { prompt ->
              inferenceMutex.withLock {
                runInferenceBlocking(activeModel!!, prompt)
              }
            }
          )
        }
      }
      
      engine?.start(wait = false)
      
      val actualPort = engine?.environment?.connectors?.firstOrNull()?.port ?: port
      _port.value = actualPort
      _isRunning.value = true
      _requestCount.value = 0
      _lastError.value = null

      val url = "http://localhost:$actualPort"
      Log.d(TAG, "Server started on $url")
      showNotification(actualPort, model.name)
      onStarted(url)

    } catch (e: java.net.BindException) {
      val msg = "Port $port is already in use. Please choose another port."
      Log.e(TAG, msg, e)
      _lastError.value = msg
      _isRunning.value = false
      onError(msg)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start server", e)
      _lastError.value = e.message
      _isRunning.value = false
      onError(e.message ?: "Failed to start server")
    }
  }

  /**
   * Stop the server.
   */
  fun stop() {
    try {
      shouldStop = true
      engine?.stop(1000, 2000)
      engine = null
      _isRunning.value = false
      _port.value = 0
      clearNotification()
      Log.d(TAG, "Server stopped")
    } catch (e: Exception) {
      Log.e(TAG, "Error stopping server", e)
    }
  }

  private fun showNotification(port: Int, modelName: String) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "Model Server Status",
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = "Shows the status of the local AI Model Server"
      }
      notificationManager.createNotificationChannel(channel)
    }

    val intent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent = PendingIntent.getActivity(
      context, 0, intent, PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("Model Server Running")
      .setContentText("Serving $modelName on port $port")
      .setOngoing(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setContentIntent(pendingIntent)
      .build()

    notificationManager.notify(NOTIFICATION_ID, notification)
  }

  private fun clearNotification() {
    notificationManager.cancel(NOTIFICATION_ID)
  }

  private fun decodeBase64ToBitmap(base64Str: String): Bitmap? {
    return try {
      val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
      BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to decode base64 image", e)
      null
    }
  }

  /**
   * Run inference and return full result.
   */
  private suspend fun runInferenceBlocking(
    model: Model, 
    input: String, 
    bitmaps: List<Bitmap> = emptyList(),
    onFirstToken: () -> Unit = {}
  ): String {
    val fullResult = StringBuilder()
    var firstTokenReported = false

    try {
      model.runtimeHelper.generateResponseStream(
        model = model,
        input = input,
        images = bitmaps,
        audioClips = emptyList(),
      ).collect { partialResult ->
        if (!firstTokenReported && partialResult.isNotEmpty()) {
            onFirstToken()
            firstTokenReported = true
        }
        if (partialResult.isNotEmpty()) {
          if (partialResult.length > fullResult.length) {
            fullResult.setLength(0)
            fullResult.append(partialResult)
          } else if (!fullResult.toString().contains(partialResult)) {
            fullResult.append(partialResult)
          }
        }
      }
      return fullResult.toString()
    } catch (e: Exception) {
      throw e
    }
  }
}
