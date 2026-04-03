package com.rigrise.gallery.data

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.util.Log
import com.rigrise.gallery.AppLifecycleProvider
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AGModelCacheManager"

@Singleton
class ModelCacheManager @Inject constructor(
  private val lifecycleProvider: AppLifecycleProvider
) : ComponentCallbacks2 {

  // LRU cache to keep track of initialized models and their cleanup callbacks
  // Use model name as key because Model is a data class with mutable fields (hashCode changes)
  private val activeModels = LinkedHashMap<String, Pair<Model, () -> Unit>>(0, 0.75f, true)
  private val MAX_ACTIVE_MODELS = 4 // Increased limit

  fun registerModel(model: Model, cleanUpFn: () -> Unit) {
    synchronized(activeModels) {
      Log.d(TAG, "Registering model in cache: ${model.name}")
      activeModels[model.name] = Pair(model, cleanUpFn)
      evictIfNeeded()
    }
  }

  fun unregisterModel(model: Model) {
    synchronized(activeModels) {
      Log.d(TAG, "Unregistering model from cache: ${model.name}")
      activeModels.remove(model.name)
    }
  }

  private fun evictIfNeeded() {
    // Must be called from synchronized block
    while (activeModels.size > MAX_ACTIVE_MODELS) {
      val eldestKey = activeModels.keys.first()
      val eldestEntry = activeModels.remove(eldestKey)
      Log.d(TAG, "Evicting model due to LRU limit: ${eldestKey}")
      eldestEntry?.second?.invoke()
    }
  }

  fun evictIdleModels() {
    synchronized(activeModels) {
      Log.d(TAG, "Evicting idle models. Foreground: ${lifecycleProvider.isAppInForeground}")
      if (!lifecycleProvider.isAppInForeground) {
        // Evict all when backgrounded
        val entriesToEvict = activeModels.values.toList()
        activeModels.clear()
        for (entry in entriesToEvict) {
          Log.d(TAG, "Evicting background model: ${entry.first.name}")
          entry.second.invoke()
        }
      } else {
        // Evict all but the most recently used
        val keysToEvict = activeModels.keys.toList().dropLast(1)
        for (key in keysToEvict) {
          Log.d(TAG, "Evicting idle model: $key")
          val entry = activeModels.remove(key)
          entry?.second?.invoke()
        }
      }
    }
  }

  override fun onTrimMemory(level: Int) {
    Log.d(TAG, "onTrimMemory called with level: $level")
    if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
        level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
      evictIdleModels()
    }
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    // No-op
  }

  override fun onLowMemory() {
    Log.d(TAG, "onLowMemory called")
    evictIdleModels()
  }
}
