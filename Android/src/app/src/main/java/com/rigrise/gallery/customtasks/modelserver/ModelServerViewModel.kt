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

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rigrise.gallery.data.DataStoreRepository
import com.rigrise.gallery.data.Model
import com.rigrise.gallery.runtime.LlmModelHelper
import com.rigrise.gallery.runtime.runtimeHelper
import com.rigrise.gallery.customtasks.mobileactions.MobileActionsTools
import com.rigrise.gallery.customtasks.mobileactions.getSystemPrompt
import com.rigrise.gallery.data.BuiltInTaskId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

data class ModelServerUiState(
  val isRunning: Boolean = false,
  val runningModelName: String = "",
  val serverUrl: String = "",
  val port: Int = 8080,
  val requestCount: Int = 0,
  val lastError: String? = null,
  val serverIp: String = "",
  val testResponse: String? = null,
  val isTesting: Boolean = false,
  val logs: List<ModelServerLog> = emptyList(),
  val requireApiKey: Boolean = true,
)

@HiltViewModel
class ModelServerViewModel @Inject constructor(
  private val dataStoreRepository: DataStoreRepository,
) : ViewModel() {

  private var openAiServer: OpenAiServer? = null

  private val _uiState = MutableStateFlow(ModelServerUiState())
  val uiState: StateFlow<ModelServerUiState> = _uiState.asStateFlow()

  private var currentModel: Model? = null
  private var currentContext: Context? = null

  init {
      _uiState.value = _uiState.value.copy(
        requireApiKey = dataStoreRepository.isModelServerApiKeyRequired()
      )
      
      viewModelScope.launch {
          ModelServerLogManager.logs.collect { _ ->
              _uiState.value = _uiState.value.copy(logs = ModelServerLogManager.getRecentLogs())
          }
      }
  }

  fun toggleRequireApiKey(context: Context, required: Boolean) {
    dataStoreRepository.setModelServerApiKeyRequired(required)
    val wasRunning = _uiState.value.isRunning
    val port = _uiState.value.port
    val model = currentModel
    
    _uiState.value = _uiState.value.copy(requireApiKey = required)
    
    // Update the running server if it exists
    if (wasRunning && model != null) {
        Log.d("ModelServerViewModel", "Restarting server due to API key requirement change")
        startServer(context, model, port, force = true)
    } else {
        openAiServer?.requireApiKey = required
    }
  }

  /**
   * Start the OpenAI-compatible server using the model's runtimeHelper.
   */
  fun startServer(context: Context, model: Model, port: Int = 8080, force: Boolean = false) {
    viewModelScope.launch {
      Log.d("ModelServerViewModel", "Starting server for model: ${model.name} on port: $port (force=$force)")
      
      _uiState.value = _uiState.value.copy(lastError = null)
      
      if (_uiState.value.isRunning) {
        if (!force && _uiState.value.runningModelName == model.name && _uiState.value.port == port) {
            _uiState.value = _uiState.value.copy(lastError = "Server is already running on port $port with this model.")
            return@launch
        }
        stopServer()
      }

      kotlinx.coroutines.delay(500)

      val helper = model.runtimeHelper
      if (helper == null) {
        _uiState.value = _uiState.value.copy(lastError = "Model runtime helper not initialized. Please initialize the model first.")
        return@launch
      }

      currentModel = model
      currentContext = context

      if (openAiServer == null) {
          openAiServer = OpenAiServer(context)
      }

      openAiServer?.start(
        port = port,
        model = model,
        requireApiKey = _uiState.value.requireApiKey,
        onStarted = { url ->
          viewModelScope.launch {
            val ip = getDeviceIpAddress(context)
            val actualPort = openAiServer?.port?.value ?: port
            val fullUrl = if (ip.isNotEmpty()) "http://$ip:$actualPort" else url

            _uiState.value = _uiState.value.copy(
              isRunning = true,
              runningModelName = model.name,
              serverUrl = fullUrl,
              port = actualPort,
              requestCount = 0,
              lastError = null,
              serverIp = ip,
            )
            
            openAiServer?.requestCount?.collect { count ->
                _uiState.value = _uiState.value.copy(requestCount = count)
            }
          }
        },
        onError = { error ->
          viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                lastError = error,
                isRunning = false
            )
          }
        },
      )
    }
  }

  fun testServer(prompt: String) {
    if (!_uiState.value.isRunning) return

    viewModelScope.launch {
      Log.d("ModelServerViewModel", "Starting HTTP test with prompt: $prompt")
      _uiState.value = _uiState.value.copy(isTesting = true, testResponse = null)
      
      try {
        val port = _uiState.value.port
        val result = withContext(Dispatchers.IO) {
            val url = URL("http://localhost:$port/v1/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            if (_uiState.value.requireApiKey) {
                conn.setRequestProperty("Authorization", "Bearer mobile@123")
            }
            conn.doOutput = true
            conn.connectTimeout = 300000 // 5 minutes
            conn.readTimeout = 300000
            
            val json = kotlinx.serialization.json.Json
            val request = ChatCompletionRequest(
                model = _uiState.value.runningModelName,
                messages = listOf(ChatMessage(role = "user", content = JsonPrimitive(prompt))),
                stream = false
            )
            
            val requestBody = json.encodeToString(ChatCompletionRequest.serializer(), request)
            conn.outputStream.write(requestBody.toByteArray())
            
            if (conn.responseCode == 200) {
                val responseBody = conn.inputStream.bufferedReader().readText()
                val response = json.decodeFromString<ChatCompletionResponse>(responseBody)
                response.choices.firstOrNull()?.message?.textContent ?: "No content"
            } else {
                val errorBody = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                "Error ${conn.responseCode}: $errorBody"
            }
        }

        _uiState.value = _uiState.value.copy(testResponse = result, isTesting = false)
      } catch (e: Exception) {
        Log.e("ModelServerViewModel", "HTTP Test failed", e)
        _uiState.value = _uiState.value.copy(testResponse = "Error: ${e.message}", isTesting = false)
      }
    }
  }

  fun clearTest() {
      _uiState.value = _uiState.value.copy(testResponse = null)
  }

  fun clearLogs() {
      ModelServerLogManager.clearLogs()
      _uiState.value = _uiState.value.copy(logs = emptyList())
  }

  /**
   * Stop the server.
   */
  fun stopServer() {
    openAiServer?.stop()
    openAiServer = null

    _uiState.value = _uiState.value.copy(
      isRunning = false,
      runningModelName = "",
      serverUrl = "",
    )
  }

  /**
   * Get device IP address for external access.
   */
  private fun getDeviceIpAddress(context: Context): String {
    try {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            // Skip loopback and inactive interfaces
            if (networkInterface.isLoopback || !networkInterface.isUp) continue
            
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                // Return the first non-loopback IPv4 address
                if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                    return address.hostAddress ?: ""
                }
            }
        }
    } catch (e: java.net.SocketException) {
        Log.e("ModelServerViewModel", "Error getting IP address", e)
    }
    return ""
  }

  override fun onCleared() {
    super.onCleared()
    stopServer()
  }
}
