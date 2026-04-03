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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// ==================== Request Models ====================

@Serializable
data class ChatCompletionRequest(
  val model: String? = null,
  val messages: List<ChatMessage>,
  val temperature: Double? = null,
  val top_p: Double? = null,
  val n: Int? = null,
  val stream: Boolean? = false,
  val max_tokens: Int? = null,
  val presence_penalty: Double? = null,
  val frequency_penalty: Double? = null,
  val user: String? = null,
  val tools: List<Tool>? = null,
  val tool_choice: String? = null,
)

@Serializable
data class Tool(
  val type: String,
  val function: FunctionDefinition,
)

@Serializable
data class FunctionDefinition(
  val name: String,
  val description: String? = null,
  val parameters: JsonElement? = null,
)

@Serializable
data class ChatMessage(
  val role: String,
  val content: JsonElement,
  val name: String? = null,
  val tool_calls: List<ToolCall>? = null,
) {
  /**
   * Extract plain text content from JsonElement, handling both String and Array formats.
   */
  val textContent: String
    get() = when (content) {
      is JsonPrimitive -> content.content
      is JsonArray -> {
        content.joinToString("") { element ->
          if (element is JsonObject) {
            val type = element["type"]?.jsonPrimitive?.content
            if (type == "text") {
              element["text"]?.jsonPrimitive?.content ?: ""
            } else ""
          } else ""
        }
      }
      else -> content.toString()
    }

  /**
   * Extract base64 image data if present.
   */
  val imageUrls: List<String>
    get() = if (content is JsonArray) {
      content.mapNotNull { element ->
        if (element is JsonObject && element["type"]?.jsonPrimitive?.content == "image_url") {
          element["image_url"]?.jsonObject?.get("url")?.jsonPrimitive?.content
        } else null
      }
    } else emptyList()
}

@Serializable
data class ToolCall(
  val id: String,
  val type: String = "function",
  val function: FunctionCall,
)

@Serializable
data class FunctionCall(
  val name: String,
  val arguments: String,
)

// ==================== Response Models ====================

@Serializable
data class ChatCompletionResponse(
  val id: String,
  @SerialName("object") val objectType: String = "chat.completion",
  val created: Long,
  val model: String,
  val choices: List<Choice>,
  val usage: Usage? = null,
)

@Serializable
data class Choice(
  val index: Int,
  val message: ChatMessage,
  @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class Usage(
  @SerialName("prompt_tokens") val promptTokens: Int? = null,
  @SerialName("completion_tokens") val completionTokens: Int? = null,
  @SerialName("total_tokens") val totalTokens: Int? = null,
)

// ==================== Streaming Response ====================

@Serializable
data class ChatCompletionChunk(
  val id: String,
  @SerialName("object") val objectType: String = "chat.completion.chunk",
  val created: Long,
  val model: String,
  val choices: List<DeltaChoice>,
)

@Serializable
data class DeltaChoice(
  val index: Int,
  val delta: DeltaContent,
  @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class DeltaContent(
  val role: String? = null,
  val content: String? = null,
  val tool_calls: List<ToolCall>? = null,
)

// ==================== Ollama Tags Response ====================

@Serializable
data class OllamaTagsResponse(
  val models: List<OllamaModel>
)

@Serializable
data class OllamaModel(
  val name: String,
  val model: String,
  val modified_at: String,
  val size: Long,
  val digest: String = "sha256:0",
  val details: OllamaModelDetails
)

@Serializable
data class OllamaModelDetails(
  val parent_model: String = "",
  val format: String = "gguf",
  val family: String = "unknown",
  val families: List<String> = emptyList(),
  val parameter_size: String = "unknown",
  val quantization_level: String = "unknown"
)

// ==================== Ollama API Models ====================

@Serializable
data class OllamaChatRequest(
  val model: String,
  val messages: List<ChatMessage>,
  val stream: Boolean? = true,
  val format: String? = null,
  val options: Map<String, JsonPrimitive>? = null,
  val keep_alive: String? = null
)

@Serializable
data class OllamaChatResponse(
  val model: String,
  val created_at: String,
  val message: ChatMessage? = null,
  val done: Boolean,
  val total_duration: Long? = null,
  val load_duration: Long? = null,
  val prompt_eval_count: Int? = null,
  val prompt_eval_duration: Long? = null,
  val eval_count: Int? = null,
  val eval_duration: Long? = null
)

@Serializable
data class OllamaGenerateRequest(
  val model: String,
  val prompt: String,
  val system: String? = null,
  val template: String? = null,
  val context: List<Int>? = null,
  val stream: Boolean? = true,
  val raw: Boolean? = false,
  val format: String? = null,
  val options: Map<String, JsonPrimitive>? = null,
  val keep_alive: String? = null
)

@Serializable
data class OllamaGenerateResponse(
  val model: String,
  val created_at: String,
  val response: String,
  val done: Boolean,
  val context: List<Int>? = null,
  val total_duration: Long? = null,
  val load_duration: Long? = null,
  val prompt_eval_count: Int? = null,
  val prompt_eval_duration: Long? = null,
  val eval_count: Int? = null,
  val eval_duration: Long? = null
)

// ==================== Models List Response ====================

@Serializable
data class ModelsListResponse(
  @SerialName("object") val objectType: String = "list",
  val data: List<ModelInfo>,
)

@Serializable
data class ModelInfo(
  val id: String,
  @SerialName("object") val objectType: String = "model",
  val created: Long,
  val owned_by: String = "rigrise",
)

// ==================== Error Response ====================

@Serializable
data class OpenAIError(
  val error: ErrorDetail,
)

@Serializable
data class ErrorDetail(
  val message: String,
  val type: String,
  val code: String? = null,
  val param: String? = null,
)

// ==================== Helper Functions ====================

fun createErrorResponse(message: String, type: String = "invalid_request_error"): String {
  val error = OpenAIError(ErrorDetail(message, type))
  return kotlinx.serialization.json.Json.encodeToString(OpenAIError.serializer(), error)
}