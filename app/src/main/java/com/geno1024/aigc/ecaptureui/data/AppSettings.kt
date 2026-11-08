package com.geno1024.aigc.ecaptureui.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

class AppSettings(private val context: Context) {
    private object Keys {
        val BINARY_PATH = stringPreferencesKey("binary_path")
        val DOWNLOAD_URL = stringPreferencesKey("download_url")
        val OUTPUT_DIR = stringPreferencesKey("output_dir")
        val DEFAULT_ARGS = stringPreferencesKey("default_args")
    }

    val binaryPath: Flow<String> = context.dataStore.data.map { it[Keys.BINARY_PATH] ?: "" }
    val downloadUrl: Flow<String> = context.dataStore.data.map {
        it[Keys.DOWNLOAD_URL] ?: "https://github.com/gojue/ecapture/releases/latest"
    }
    val outputDir: Flow<String> = context.dataStore.data.map { it[Keys.OUTPUT_DIR] ?: "" }
    val defaultArgs: Flow<String> = context.dataStore.data.map { it[Keys.DEFAULT_ARGS] ?: "tls" }

    suspend fun setBinaryPath(value: String) = context.dataStore.edit { it[Keys.BINARY_PATH] = value }
    suspend fun setDownloadUrl(value: String) = context.dataStore.edit { it[Keys.DOWNLOAD_URL] = value }
    suspend fun setOutputDir(value: String) = context.dataStore.edit { it[Keys.OUTPUT_DIR] = value }
    suspend fun setDefaultArgs(value: String) = context.dataStore.edit { it[Keys.DEFAULT_ARGS] = value }
}
