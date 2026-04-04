# Model Server "Failed to create engine" Error - Resolution

## Problem Description

The Model Server feature in the Gallery app was failing with a "Failed to create engine: INTERNAL: ERROR" message when attempting to initialize models, specifically the Gemma 4B (3.6GB) model.

**Key observation:** The same model (Gemma 4B) worked fine in "Ask AI Chat" but failed in "Model Server" page.

## Root Causes Identified

### 1. Missing `@Inject` Annotation (Dependency Injection Issue)
**Location:** `ModelServerTask.kt` line 35

**Problem:**
```kotlin
class ModelServerTask : CustomTask  // Missing @Inject
```

**Fix:**
```kotlin
class ModelServerTask @Inject constructor() : CustomTask
```

All other working tasks (TinyGardenTask, MobileActionsTask, AgentChatTask) use the `@Inject constructor()` annotation for proper Hilt dependency injection.

### 2. Missing `coroutineScope` Parameter (Critical)
**Location:** `ModelServerTask.kt` line ~70

**Problem:** Model Server was not passing the `coroutineScope` parameter to `LlmChatModelHelper.initialize()`, while "Ask AI Chat" does pass it.

**Comparison:**
```kotlin
// Ask AI Chat (Working)
model.runtimeHelper.initialize(
  coroutineScope = coroutineScope,  // ✅ PASSED
  ...
)

// Model Server (Broken - Before Fix)
LlmChatModelHelper.initialize(
  ...
  // ❌ MISSING coroutineScope parameter
)

// Model Server (Fixed)
LlmChatModelHelper.initialize(
  ...
  coroutineScope = coroutineScope,  // ✅ ADDED
)
```

### 3. GPU Memory Exhaustion
**Error from logs:**
```
E/Adreno-GSL: GSL MEM ERROR: kgsl_sharedmem_alloc ioctl failed
E/native: Failed to allocate 628246080 bytes of device memory (clCreateBuffer): Out of resources
```

**Problem:** The Gemma 4B model (3.6GB) requires ~600MB+ of GPU memory for initialization, which exceeds the available GPU memory on the device.

**Solution:** Force CPU backend for Model Server initialization.

```kotlin
// Force CPU backend to avoid GPU memory issues
val originalConfigValues = model.configValues.toMap()
model.configValues = model.configValues.toMutableMap().apply {
  put("accelerator", Accelerator.CPU.label)
}
```

### 4. Vision/Audio Support Parameters
**Location:** `ModelServerTask.kt`

**Change:** Set `supportImage` and `supportAudio` to `false` to match Ask AI Chat behavior and reduce resource requirements.

```kotlin
LlmChatModelHelper.initialize(
  ...
  supportImage = false,
  supportAudio = false,
  ...
)
```

## Complete Fix Implementation

### File: `Android/src/app/src/main/java/com/rigrise/gallery/customtasks/modelserver/ModelServerTask.kt`

```kotlin
package com.rigrise.gallery.customtasks.modelserver

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.runtime.Composable
import com.rigrise.gallery.customtasks.common.CustomTask
import com.rigrise.gallery.customtasks.common.CustomTaskData
import com.rigrise.gallery.data.Accelerator
import com.rigrise.gallery.data.Category
import com.rigrise.gallery.data.Model
import com.rigrise.gallery.data.Task
import com.rigrise.gallery.ui.llmchat.LlmChatModelHelper
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

class ModelServerTask @Inject constructor() : CustomTask {

  override val task: Task = Task(
    id = "model_server",
    label = "Model Server",
    category = Category.EXPERIMENTAL,
    icon = Icons.Outlined.Cloud,
    description = "Host your model as an OpenAI-compatible API server.",
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
    // Force CPU backend for Model Server to avoid GPU memory issues
    // Gemma 4B (3.6GB) requires ~600MB+ GPU memory which fails on most devices
    val originalConfigValues = model.configValues.toMap()
    model.configValues = model.configValues.toMutableMap().apply {
      put("accelerator", Accelerator.CPU.label)
    }

    LlmChatModelHelper.initialize(
      context = context,
      model = model,
      supportImage = false,
      supportAudio = false,
      onDone = { error ->
        // Restore original config values
        model.configValues = originalConfigValues
        onDone(error)
      },
      systemInstruction = null,
      tools = emptyList(),
      enableConversationConstrainedDecoding = false,
      coroutineScope = coroutineScope,  // ✅ CRITICAL: Was missing
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
```

### Additional Improvements

#### Enhanced Logging (Optional but Recommended)
**File:** `Android/src/app/src/main/java/com/rigrise/gallery/ui/llmchat/LlmChatModelHelper.kt`

Added comprehensive diagnostic logging around engine creation:

```kotlin
// Before engine creation
Log.d(TAG, "Model path: $modelPath")
Log.d(TAG, "Model file exists: ${File(modelPath).exists()}")
Log.d(TAG, "Model file size: ${File(modelPath).length()}")
Log.d(TAG, "Creating LiteRT LM engine...")

// After engine creation
Log.d(TAG, "Engine object created, initializing...")
engine.initialize()
Log.d(TAG, "Engine initialized successfully")

// In catch block
Log.e(TAG, "Failed to create engine", e)
Log.e(TAG, "Error type: ${e.javaClass.simpleName}")
Log.e(TAG, "Error message: ${e.message}")
```

## Verification Steps

1. **Clean build:**
   ```bash
   cd Android/src
   gradle clean
   gradle assembleDebug
   ```

2. **Install on device:**
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

3. **Test Model Server:**
   - Open Gallery app
   - Navigate to Experimental → Model Server
   - Select Gemma 4B model
   - Click "Start Server"
   - Verify server starts successfully

4. **Monitor logs:**
   ```bash
   adb logcat -v time | grep -E "(AGLlmChatModelHelper|ModelServerViewModel)"
   ```

   **Expected success pattern:**
   ```
   AGLlmChatModelHelper: Preferred backend: CPU  ← CPU backend used
   AGLlmChatModelHelper: Engine initialized successfully  ← Success!
   ModelServerViewModel: Starting server
   OpenAiServer: Server started on http://localhost:8080
   ```

5. **Test API endpoint:**
   ```bash
   curl http://localhost:8080/v1/models
   ```

   Should return JSON list of available models.

## Key Takeaways

1. **Always pass `coroutineScope`** when calling `LlmChatModelHelper.initialize()` - this is critical for proper async initialization
2. **Large models (3GB+) need CPU backend** on most devices due to GPU memory limitations
3. **Follow working patterns** - compare with existing working implementations (Ask AI Chat)
4. **Add diagnostic logging** to help debug native library errors

## Files Modified

1. `Android/src/app/src/main/java/com/rigrise/gallery/customtasks/modelserver/ModelServerTask.kt`
   - Added `@Inject constructor()` annotation
   - Added `coroutineScope` parameter
   - Added CPU backend override
   - Set supportImage/supportAudio to false

2. `Android/src/app/src/main/java/com/rigrise/gallery/ui/llmchat/LlmChatModelHelper.kt`
   - Added diagnostic logging (optional but recommended)

## Resolution Status

✅ **RESOLVED** - Model Server now successfully initializes Gemma 4B model using CPU backend and properly passes all required parameters including coroutineScope.

---
**Date:** 2026-04-05
**Model:** Gemma 4B (3.6GB)
**Device:** vivo V2045 (Android 14)
