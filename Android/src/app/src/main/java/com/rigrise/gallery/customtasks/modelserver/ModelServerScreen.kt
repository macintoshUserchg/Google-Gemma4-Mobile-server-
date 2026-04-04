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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.rigrise.gallery.R
import com.rigrise.gallery.data.Model
import com.rigrise.gallery.data.ModelDownloadStatusType
import com.rigrise.gallery.data.Task
import com.rigrise.gallery.ui.modelmanager.ModelInitializationStatusType
import com.rigrise.gallery.ui.modelmanager.ModelManagerViewModel

import com.rigrise.gallery.ui.common.ConfigDialog
import com.rigrise.gallery.ui.common.RotationalLoader
import com.rigrise.gallery.data.convertValueToTargetType
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelServerScreen(
  modelManagerViewModel: ModelManagerViewModel,
  task: Task,
  viewModel: ModelServerViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsState()
  val mmUiState by modelManagerViewModel.uiState.collectAsState()
  val context = LocalContext.current

  var portText by rememberSaveable { mutableStateOf("8080") }
  var showHelpDialog by remember { mutableStateOf(false) }
  var selectedTab by rememberSaveable { mutableStateOf(0) }
  var configModel by remember { mutableStateOf<Model?>(null) }

  val isAnyModelInitializing = mmUiState.modelInitializationStatus.values.any { it.status == ModelInitializationStatusType.INITIALIZING }

  if (isAnyModelInitializing) {
    Dialog(onDismissRequest = { /* Do not dismiss */ }) {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(
          modifier = Modifier.padding(32.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
          RotationalLoader(size = 64.dp)
          Text(
            text = "Applying configurations...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
        }
      }
    }
  }

  if (showHelpDialog) {
    HowToUseDialog(
      uiState = uiState,
      onDismiss = { showHelpDialog = false }
    )
  }

  configModel?.let { currentConfigModel ->
    val modelConfigs = currentConfigModel.configs.toMutableList()
    
    ConfigDialog(
      title = "Configurations",
      configs = modelConfigs,
      initialValues = currentConfigModel.configValues,
      onDismissed = { configModel = null },
      onOk = { curConfigValues, _, _ ->
        configModel = null
        
        var needReinitialization = false
        var same = true
        for (config in modelConfigs) {
          val key = config.key.label
          val oldValue = convertValueToTargetType(
            value = currentConfigModel.configValues.getValue(key),
            valueType = config.valueType,
          )
          val newValue = convertValueToTargetType(
            value = curConfigValues.getValue(key),
            valueType = config.valueType,
          )
          if (oldValue != newValue) {
            same = false
            if (config.needReinitialization) {
              needReinitialization = true
            }
            break
          }
        }

        if (!same) {
          val oldConfigValues = currentConfigModel.configValues
          currentConfigModel.prevConfigValues = oldConfigValues
          currentConfigModel.configValues = curConfigValues
          modelManagerViewModel.updateConfigValuesUpdateTrigger()

          if (needReinitialization) {
            val wasRunning = uiState.isRunning && uiState.runningModelName == currentConfigModel.name
            if (wasRunning) {
              viewModel.stopServer()
            }
            
            // Re-initialize with force=true to rebuild engine with new configs (like GPU)
            modelManagerViewModel.initializeModel(
              context = context,
              task = task,
              model = currentConfigModel,
              force = true,
              onDone = {
                if (wasRunning) {
                   val port = portText.toIntOrNull() ?: 8080
                   viewModel.startServer(context, currentConfigModel, port)
                }
              }
            )
          }
        }
      },
      showSystemPromptEditorTab = false
    )
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .imePadding()
      .padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(48.dp))
        Text(
          text = stringResource(R.string.model_server_title),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = { showHelpDialog = true }) {
            Icon(Icons.Default.Info, contentDescription = "Help", tint = MaterialTheme.colorScheme.primary)
        }
    }

    Text(
      text = stringResource(R.string.model_server_description),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(12.dp))

    TabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
            Text("Control", modifier = Modifier.padding(12.dp))
        }
        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Inspector")
                if (uiState.logs.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Badge { Text(uiState.logs.size.toString()) }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    if (selectedTab == 0) {
        // --- CONTROL TAB (All scrollable together) ---
        // Derive downloaded models from mmUiState to ensure recomputation when download status changes
        val downloadedModels = remember(mmUiState.modelDownloadStatus) {
          modelManagerViewModel.getAllModels().filter {
            mmUiState.modelDownloadStatus[it.name]?.status == ModelDownloadStatusType.SUCCEEDED && it.isLlm
          }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // 1. Server Configuration & Status
            item(key = "server_config") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (uiState.isRunning) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Cloud, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Server Active on Port ${uiState.port}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("URL: ${uiState.serverUrl}", style = MaterialTheme.typography.bodyMedium)
                            Text("Model: ${uiState.runningModelName}", style = MaterialTheme.typography.bodySmall)
                            Text("Requests: ${uiState.requestCount}", style = MaterialTheme.typography.bodySmall)
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.stopServer() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Stop, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Stop Server")
                            }
                        } else {
                            OutlinedTextField(
                                value = portText,
                                onValueChange = { portText = it.filter { c -> c.isDigit() }.take(5) },
                                label = { Text("Server Port") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Require API Key", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "If enabled, clients must provide a Bearer token: mobile@123",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = uiState.requireApiKey,
                                    onCheckedChange = { viewModel.toggleRequireApiKey(context, it) }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Enter a port (e.g., 8080) and select a model below to start.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            // 2. Error message (Conditional)
            if (uiState.lastError != null) {
                item(key = "error_message") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uiState.lastError!!,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // 3. Test Interface (Conditional)
            if (uiState.isRunning) {
                item(key = "test_interface") {
                    TestServerCard(uiState = uiState, onTest = { viewModel.testServer(it) }, onClear = { viewModel.clearTest() })
                }
            }

            // 4. Downloaded Models List Title
            item(key = "models_list_title") {
                Text(
                  text = stringResource(R.string.model_server_downloaded_models),
                  style = MaterialTheme.typography.titleSmall,
                  modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                  fontWeight = FontWeight.Bold
                )
            }

            // 5. Models List
            if (downloadedModels.isEmpty()) {
                item(key = "no_models_message") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                          text = stringResource(R.string.model_server_no_models),
                          modifier = Modifier.padding(16.dp),
                          style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(downloadedModels, key = { it.name }) { model ->
                    val isRunning = uiState.isRunning && uiState.runningModelName == model.name
                    val isInitializing = mmUiState.isModelInitializing(model)
                    
                    ModelServerItem(
                        model = model,
                        isRunning = isRunning,
                        isInitializing = isInitializing,
                        anyServerRunning = uiState.isRunning,
                        onStart = {
                            val port = portText.toIntOrNull() ?: 8080
                            val initStatus = mmUiState.modelInitializationStatus[model.name]
                            
                            if (initStatus?.status == ModelInitializationStatusType.INITIALIZED) {
                                viewModel.startServer(context, model, port)
                            } else {
                                modelManagerViewModel.initializeModel(context, task, model) {
                                    viewModel.startServer(context, model, port)
                                }
                            }
                        },
                        onStop = { viewModel.stopServer() },
                        onConfigClick = { configModel = model }
                    )
                }
            }
        }
    } else {
        // --- INSPECTOR TAB ---
        ModelServerInspector(logs = uiState.logs, onClear = { viewModel.clearLogs() })
    }
  }
}

@Composable
fun ModelServerInspector(logs: List<ModelServerLog>, onClear: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Request Logs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Delete, "Clear Logs", tint = MaterialTheme.colorScheme.error)
            }
        }
        
        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Text("No requests captured yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(logs) { log ->
                    LogItem(log)
                }
            }
        }
    }
}

@Composable
fun LogItem(log: ModelServerLog) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = if (log.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (log.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = log.method,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = log.path, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
                Text(text = log.timestamp, style = MaterialTheme.typography.labelSmall)
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "${log.latencyMs}ms", style = MaterialTheme.typography.labelSmall)
                if (log.ttftMs > 0) {
                    Text(text = "TTFT: ${log.ttftMs}ms", style = MaterialTheme.typography.labelSmall)
                }
                if (log.tps > 0) {
                    Text(text = "%.1f tokens/s".format(log.tps), style = MaterialTheme.typography.labelSmall)
                }
            }
            
            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text("Request:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = log.requestBody ?: "{}",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    
                    Text("Response:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = log.responseBody ?: "{}",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TestServerCard(uiState: ModelServerUiState, onTest: (String) -> Unit, onClear: () -> Unit) {
    var testPrompt by rememberSaveable { mutableStateOf("Hi, tell me a short fact.") }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Test API Server",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = testPrompt,
                onValueChange = { testPrompt = it },
                label = { Text("Test Prompt") },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { onTest(testPrompt) },
                    enabled = !uiState.isTesting && testPrompt.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Send HTTP Request")
                }
                if (uiState.testResponse != null) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Clear, "Clear", modifier = Modifier.size(18.dp))
                    }
                }
            }
            
            if (uiState.testResponse != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.small,
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Text(
                        text = uiState.testResponse!!,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun HowToUseDialog(uiState: ModelServerUiState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How to Use") },
        text = {
            val instructions = stringResource(
                R.string.model_server_usage_instructions,
                uiState.serverUrl.ifEmpty { "http://<device-ip>:${uiState.port}" },
                uiState.runningModelName.ifEmpty { "<model-id>" }
            )
            val apiKeyInfo = if (uiState.requireApiKey) {
                "\n\n<b>Authentication:</b>\nInclude the header <code>Authorization: Bearer mobile@123</code>"
            } else {
                "\n\n<b>Authentication:</b>\nDisabled (No API key required)"
            }
            Text(HtmlCompat.fromHtml(instructions + apiKeyInfo, HtmlCompat.FROM_HTML_MODE_LEGACY).toString())
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        }
    )
}

@Composable
fun ModelServerItem(
  model: Model,
  isRunning: Boolean,
  isInitializing: Boolean,
  anyServerRunning: Boolean,
  onStart: () -> Unit,
  onStop: () -> Unit,
  onConfigClick: () -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = if (isRunning) 
        MaterialTheme.colorScheme.primaryContainer 
      else 
        MaterialTheme.colorScheme.surfaceVariant,
    ),
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = model.displayName.ifEmpty { model.name },
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold
        )
        if (model.info.isNotEmpty()) {
          Text(
            text = model.info,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
          )
        }
      }

      Spacer(modifier = Modifier.width(8.dp))

      if (model.configs.isNotEmpty()) {
        IconButton(onClick = onConfigClick, enabled = !isRunning && !isInitializing) {
          Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Configure Model",
            tint = if (isRunning || isInitializing) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary
          )
        }
      }

      if (isInitializing) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
      } else {
        Button(
          onClick = { if (isRunning) onStop() else onStart() },
          enabled = !anyServerRunning || isRunning,
          colors = if (isRunning) 
            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
          else 
            ButtonDefaults.buttonColors(),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
          Icon(
            imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(text = if (isRunning) stringResource(R.string.model_server_stop) else stringResource(R.string.model_server_start), style = MaterialTheme.typography.labelMedium)
        }
      }
    }
  }
}

private fun copyToClipboard(context: Context, text: String) {
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  val clip = ClipData.newPlainText("Server URL", text)
  clipboard.setPrimaryClip(clip)
}
