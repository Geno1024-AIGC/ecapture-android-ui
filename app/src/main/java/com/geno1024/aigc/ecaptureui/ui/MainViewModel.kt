package com.geno1024.aigc.ecaptureui.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geno1024.aigc.ecaptureui.capture.CaptureService
import com.geno1024.aigc.ecaptureui.capture.CaptureState
import com.geno1024.aigc.ecaptureui.data.AppSettings
import com.geno1024.aigc.ecaptureui.ecapture.BinaryManager
import com.geno1024.aigc.ecaptureui.ecapture.ReleaseClient
import com.geno1024.aigc.ecaptureui.root.Su
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

data class UiState(
    val binaryPath: String = "",
    val downloadUrl: String = "",
    val outputDir: String = "",
    val args: String = "tls",
    val rootAvailable: Boolean? = null,
    val busy: String = "",
    val message: String = "",
    val logs: List<String> = emptyList(),
    val captureState: CaptureState = CaptureState.Idle,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = AppSettings(app)
    private val releases = ReleaseClient()
    private val binaries = BinaryManager(app)

    private val _busy = MutableStateFlow("")
    private val _message = MutableStateFlow("")
    private val _root = MutableStateFlow<Boolean?>(null)
    private val _logs = MutableStateFlow<List<String>>(emptyList())

    @Suppress("UNCHECKED_CAST")
    val state: StateFlow<UiState> = combine(
        settings.binaryPath, settings.downloadUrl, settings.outputDir, settings.defaultArgs,
        _busy, _message, _root, CaptureService.state, _logs,
    ) { values ->
        UiState(
            binaryPath = values[0] as String,
            downloadUrl = values[1] as String,
            outputDir = values[2] as String,
            args = values[3] as String,
            busy = values[4] as String,
            message = values[5] as String,
            rootAvailable = values[6] as Boolean?,
            captureState = values[7] as CaptureState,
            logs = values[8] as List<String>,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    init {
        viewModelScope.launch {
            CaptureService.events.collect { line ->
                _logs.value = (_logs.value + line).takeLast(500)
            }
        }
    }

    fun setArgs(value: String) = viewModelScope.launch { settings.setDefaultArgs(value) }
    fun setDownloadUrl(value: String) = viewModelScope.launch { settings.setDownloadUrl(value) }
    fun setOutputDir(value: String) = viewModelScope.launch { settings.setOutputDir(value) }
    fun setBinaryPath(value: String) = viewModelScope.launch { settings.setBinaryPath(value) }
    fun clearLogs() { _logs.value = emptyList() }

    fun checkRoot() = viewModelScope.launch {
        _busy.value = "Checking root"
        _root.value = Su.available()
        _message.value = if (_root.value == true) "Root available" else "Root unavailable (su failed)"
        _busy.value = ""
    }

    fun installFromUri(uri: Uri) = viewModelScope.launch {
        _busy.value = "Installing binary"
        runCatching {
            val ctx = getApplication<Application>()
            val tmp = File(ctx.cacheDir, "ecapture-import")
            ctx.contentResolver.openInputStream(uri)!!.use { input ->
                FileOutputStream(tmp).use { input.copyTo(it) }
            }
            val dst = binaries.installFromFile(tmp).getOrThrow()
            settings.setBinaryPath(dst.absolutePath)
            tmp.delete()
            _message.value = "Installed: ${dst.absolutePath}"
        }.onFailure { _message.value = it.message ?: "install failed" }
        _busy.value = ""
    }

    fun downloadLatest() = viewModelScope.launch {
        _busy.value = "Fetching release"
        runCatching {
            val rel = releases.latest(state.value.downloadUrl)
            val asset = releases.suggestAsset(rel.assets, binaries.deviceAbi())
                ?: error("No matching asset for ABI ${binaries.deviceAbi()} in ${rel.tagName}; pick URL/import manually")
            _busy.value = "Downloading ${asset.name}"
            val tmp = File(getApplication<Application>().cacheDir, asset.name)
            releases.download(asset.browserDownloadUrl, tmp)
            val dst = binaries.installFromFile(tmp).getOrThrow()
            settings.setBinaryPath(dst.absolutePath)
            tmp.delete()
            _message.value = "Installed ${rel.tagName}: ${dst.absolutePath}"
        }.onFailure { _message.value = it.message ?: "download failed" }
        _busy.value = ""
    }

    fun checkBinary() = viewModelScope.launch {
        _busy.value = "Checking binary"
        val path = state.value.binaryPath.ifBlank { binaries.installedBinary().absolutePath }
        binaries.check(File(path)).onSuccess { _message.value = it }
            .onFailure { _message.value = it.message ?: "binary check failed" }
        _busy.value = ""
    }

    fun startCapture() {
        val s = state.value
        val binary = s.binaryPath.ifBlank { binaries.installedBinary().absolutePath }
        val out = s.outputDir.ifBlank { File(getApplication<Application>().filesDir, "captures").absolutePath }
        CaptureService.start(getApplication(), binary, s.args, out)
    }

    fun stopCapture() = CaptureService.stop(getApplication())
}
