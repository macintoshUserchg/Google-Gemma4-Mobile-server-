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

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class ModelServerLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
    val method: String,
    val path: String,
    val requestBody: String? = null,
    val responseBody: String? = null,
    val latencyMs: Long = 0,
    val isError: Boolean = false,
    val ttftMs: Long = 0,
    val tps: Double = 0.0
)

object ModelServerLogManager {
    private val _logs = MutableSharedFlow<ModelServerLog>(replay = 10)
    val logs = _logs.asSharedFlow()

    private val logList = mutableListOf<ModelServerLog>()

    suspend fun addLog(log: ModelServerLog) {
        logList.add(0, log)
        if (logList.size > 50) logList.removeAt(logList.size - 1)
        _logs.emit(log)
    }

    fun getRecentLogs(): List<ModelServerLog> = logList.toList()
    
    fun clearLogs() {
        logList.clear()
    }
}
