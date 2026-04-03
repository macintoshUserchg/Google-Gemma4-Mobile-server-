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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.runtime.Composable
import com.rigrise.gallery.customtasks.common.CustomTask
import com.rigrise.gallery.customtasks.common.CustomTaskData
import com.rigrise.gallery.data.Category
import com.rigrise.gallery.data.Model
import com.rigrise.gallery.data.Task
import com.rigrise.gallery.ui.llmchat.LlmChatModelHelper
import kotlinx.coroutines.CoroutineScope

/**
 * CustomTask that provides an OpenAI-compatible HTTP server
 * for the active model, allowing other agents/tools to use it.
 */
class ModelServerTask : CustomTask {

  override val task: Task =
    Task(
      id = "model_server",
      label = "Model Server",
      category = Category.EXPERIMENTAL,
      icon = Icons.Outlined.Cloud,
      description =
        "Host your model as an OpenAI-compatible API server. " +
          "Other agents and tools can connect to your device and use the model " +
          "as if it were an OpenAI endpoint.",
      docUrl = "https://github.com/rigrise/gallery",
      sourceCodeUrl = "https://github.com/rigrise/gallery",
      models = mutableListOf(),
      experimental = true,
    )

  override fun initializeModelFn(
    context: Context,
    coroutineScope: CoroutineScope,
    model: Model,
    onDone: (error: String) -> Unit,
  ) {
    LlmChatModelHelper.initialize(
      context = context,
      model = model,
      supportImage = model.llmSupportImage,
      supportAudio = model.llmSupportAudio,
      onDone = { error ->
        onDone(error)
      },
    )
  }

  override fun cleanUpModelFn(
    context: Context,
    coroutineScope: CoroutineScope,
    model: Model,
    onDone: () -> Unit,
  ) {
    LlmChatModelHelper.cleanUp(model = model, onDone = onDone)
  }

  @Composable
  override fun MainScreen(data: Any) {
    val customData = data as CustomTaskData
    val modelManagerViewModel = customData.modelManagerViewModel
    ModelServerScreen(modelManagerViewModel = modelManagerViewModel, task = this.task)
  }
}