/*
 * Copyright 2025 Rigrise
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.rigrise.gallery.customtasks.modelserver

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

// MCP server info
private const val MCP_VERSION = "2024-11-05"
private const val SERVER_NAME = "rigrise-ai-edge"
private const val SERVER_VERSION = "1.0.0"

/**
 * Registers MCP (Model Context Protocol) JSON-RPC 2.0 routes on the existing Ktor router.
 * Exposes the on-device LLM as an MCP tool named "generate".
 *
 * Clients connect to POST /mcp
 */
fun Route.mcpRoutes(
    getModelName: () -> String,
    runInference: suspend (String) -> String,
) {
    post("/mcp") {
        val body = call.receiveText()
        val request = try {
            json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            call.respondText(
                errorResponse(null, -32700, "Parse error"),
                ContentType.Application.Json, HttpStatusCode.OK
            )
            return@post
        }

        val id = request["id"]
        val method = request["method"]?.jsonPrimitive?.contentOrNull
        val params = request["params"]?.jsonObject

        val result: String = when (method) {
            "initialize" -> initializeResponse(id)
            "notifications/initialized" -> { call.respond(HttpStatusCode.NoContent); return@post }
            "tools/list" -> toolsListResponse(id)
            "tools/call" -> {
                val toolName = params?.get("name")?.jsonPrimitive?.contentOrNull
                val arguments = params?.get("arguments")?.jsonObject
                when (toolName) {
                    "generate" -> {
                        val prompt = arguments?.get("prompt")?.jsonPrimitive?.contentOrNull
                            ?: return@post call.respondText(
                                errorResponse(id, -32602, "Missing required argument: prompt"),
                                ContentType.Application.Json
                            )
                        val output = runInference(prompt)
                        toolCallResponse(id, output)
                    }
                    else -> errorResponse(id, -32602, "Unknown tool: $toolName")
                }
            }
            else -> errorResponse(id, -32601, "Method not found: $method")
        }

        call.respondText(result, ContentType.Application.Json, HttpStatusCode.OK)
    }

    // SSE endpoint for MCP clients that prefer streaming transport
    get("/mcp/sse") {
        call.response.header("Cache-Control", "no-cache")
        call.response.header("Connection", "keep-alive")
        call.respondTextWriter(ContentType.Text.EventStream) {
            write("data: ${serverInfoEvent(getModelName())}\n\n")
            flush()
        }
    }
}

private fun initializeResponse(id: JsonElement?) = buildJsonObject {
    put("jsonrpc", "2.0")
    id?.let { put("id", it) }
    put("result", buildJsonObject {
        put("protocolVersion", MCP_VERSION)
        put("serverInfo", buildJsonObject {
            put("name", SERVER_NAME)
            put("version", SERVER_VERSION)
        })
        put("capabilities", buildJsonObject {
            put("tools", buildJsonObject {})
        })
    })
}.toString()

private fun toolsListResponse(id: JsonElement?) = buildJsonObject {
    put("jsonrpc", "2.0")
    id?.let { put("id", it) }
    put("result", buildJsonObject {
        put("tools", buildJsonArray {
            add(buildJsonObject {
                put("name", "generate")
                put("description", "Generate a response from the on-device LLM (Rigrise AI Edge)")
                put("inputSchema", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("prompt", buildJsonObject {
                            put("type", "string")
                            put("description", "The prompt to send to the model")
                        })
                    })
                    put("required", buildJsonArray { add("prompt") })
                })
            })
        })
    })
}.toString()

private fun toolCallResponse(id: JsonElement?, output: String) = buildJsonObject {
    put("jsonrpc", "2.0")
    id?.let { put("id", it) }
    put("result", buildJsonObject {
        put("content", buildJsonArray {
            add(buildJsonObject {
                put("type", "text")
                put("text", output)
            })
        })
        put("isError", false)
    })
}.toString()

private fun errorResponse(id: JsonElement?, code: Int, message: String) = buildJsonObject {
    put("jsonrpc", "2.0")
    id?.let { put("id", it) }
    put("error", buildJsonObject {
        put("code", code)
        put("message", message)
    })
}.toString()

private fun serverInfoEvent(modelName: String) = buildJsonObject {
    put("type", "server_info")
    put("serverName", SERVER_NAME)
    put("modelName", modelName)
    put("protocolVersion", MCP_VERSION)
}.toString()
