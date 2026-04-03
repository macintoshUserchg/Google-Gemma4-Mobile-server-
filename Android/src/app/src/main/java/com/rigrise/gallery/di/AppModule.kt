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

package com.rigrise.gallery.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import com.rigrise.gallery.AppLifecycleProvider
import com.rigrise.gallery.BenchmarkResultsSerializer
import com.rigrise.gallery.CutoutsSerializer
import com.rigrise.gallery.GalleryLifecycleProvider
import com.rigrise.gallery.SettingsSerializer
import com.rigrise.gallery.UserDataSerializer
import com.rigrise.gallery.data.DataStoreRepository
import com.rigrise.gallery.data.DefaultDataStoreRepository
import com.rigrise.gallery.data.DefaultDownloadRepository
import com.rigrise.gallery.data.DownloadRepository
import com.rigrise.gallery.proto.BenchmarkResults
import com.rigrise.gallery.proto.CutoutCollection
import com.rigrise.gallery.proto.Settings
import com.rigrise.gallery.proto.UserData
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object AppModule {

  // Provides the SettingsSerializer
  @Provides
  @Singleton
  fun provideSettingsSerializer(): Serializer<Settings> {
    return SettingsSerializer
  }

  // Provides the CutoutSerializer
  @Provides
  @Singleton
  fun provideCutoutSerializer(): Serializer<CutoutCollection> {
    return CutoutsSerializer
  }

  // Provides the UserDataSerializer
  @Provides
  @Singleton
  fun provideUserDataSerializer(): Serializer<UserData> {
    return UserDataSerializer
  }

  // Provides the BenchmarkResultsSerializer
  @Provides
  @Singleton
  fun provideBenchmarkResultsSerializer(): Serializer<BenchmarkResults> {
    return BenchmarkResultsSerializer
  }

  // Provides DataStore<Settings>
  @Provides
  @Singleton
  fun provideSettingsDataStore(
    @ApplicationContext context: Context,
    settingsSerializer: Serializer<Settings>,
  ): DataStore<Settings> {
    return DataStoreFactory.create(
      serializer = settingsSerializer,
      produceFile = { context.dataStoreFile("settings.pb") },
    )
  }

  // Provides DataStore<CutoutCollection>
  @Provides
  @Singleton
  fun provideCutoutsDataStore(
    @ApplicationContext context: Context,
    cutoutsSerializer: Serializer<CutoutCollection>,
  ): DataStore<CutoutCollection> {
    return DataStoreFactory.create(
      serializer = cutoutsSerializer,
      produceFile = { context.dataStoreFile("cutouts.pb") },
    )
  }

  // Provides DataStore<UserData>
  @Provides
  @Singleton
  fun provideUserDataDataStore(
    @ApplicationContext context: Context,
    userDataSerializer: Serializer<UserData>,
  ): DataStore<UserData> {
    return DataStoreFactory.create(
      serializer = userDataSerializer,
      produceFile = { context.dataStoreFile("user_data.pb") },
    )
  }

  // Provides DataStore<BenchmarkResults>
  @Provides
  @Singleton
  fun provideBenchmarkResultsDataStore(
    @ApplicationContext context: Context,
    benchmarkResultsSerializer: Serializer<BenchmarkResults>,
  ): DataStore<BenchmarkResults> {
    return DataStoreFactory.create(
      serializer = benchmarkResultsSerializer,
      produceFile = { context.dataStoreFile("benchmark_results.pb") },
    )
  }

  // Provides AppLifecycleProvider
  @Provides
  @Singleton
  fun provideAppLifecycleProvider(): AppLifecycleProvider {
    return GalleryLifecycleProvider()
  }

  // Provides DataStoreRepository
  @Provides
  @Singleton
  fun provideDataStoreRepository(
    dataStore: DataStore<Settings>,
    userDataDataStore: DataStore<UserData>,
    cutoutsDataStore: DataStore<CutoutCollection>,
    benchmarkResultsStore: DataStore<BenchmarkResults>,
  ): DataStoreRepository {
    return DefaultDataStoreRepository(
      dataStore,
      userDataDataStore,
      cutoutsDataStore,
      benchmarkResultsStore,
    )
  }

  // Provides DownloadRepository
  @Provides
  @Singleton
  fun provideDownloadRepository(
    @ApplicationContext context: Context,
    lifecycleProvider: AppLifecycleProvider,
  ): DownloadRepository {
    return DefaultDownloadRepository(context, lifecycleProvider)
  }
}
