package com.geno1024.aigc.ecaptureui.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.geno1024.aigc.ecaptureui.capture.CaptureState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(state: UiState, vm: MainViewModel) {
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.installFromUri(uri)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("eCapture UI") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Environment", style = MaterialTheme.typography.titleMedium)
                        Text("Root: ${state.rootAvailable?.toString() ?: "unknown"}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = vm::checkRoot, enabled = state.busy.isEmpty()) { Text("Check root") }
                            OutlinedButton(onClick = vm::checkBinary, enabled = state.busy.isEmpty()) { Text("Check binary") }
                        }
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("eCapture binary", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = state.binaryPath,
                            onValueChange = vm::setBinaryPath,
                            label = { Text("Installed binary path") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = state.downloadUrl,
                            onValueChange = vm::setDownloadUrl,
                            label = { Text("Release source URL / repo") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = vm::downloadLatest, enabled = state.busy.isEmpty()) { Text("Download/update") }
                            OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, enabled = state.busy.isEmpty()) { Text("Import file") }
                        }
                        Text("No bundled ecapture. Use root to install/chmod after download/import.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Capture", style = MaterialTheme.typography.titleMedium)
                        val running = state.captureState as? CaptureState.Running
                        Text(
                            when (val cs = state.captureState) {
                                CaptureState.Idle -> "Idle"
                                is CaptureState.Running -> "Running pid=${cs.pid}\n${cs.outputFile}"
                                is CaptureState.Error -> "Error: ${cs.message}"
                            }
                        )
                        OutlinedTextField(
                            value = state.args,
                            onValueChange = vm::setArgs,
                            label = { Text("ecapture args") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = state.outputDir,
                            onValueChange = vm::setOutputDir,
                            label = { Text("Output directory (blank = app files/captures)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = vm::startCapture, enabled = running == null && state.busy.isEmpty()) { Text("Start") }
                            OutlinedButton(onClick = vm::stopCapture, enabled = running != null) { Text("Stop") }
                            OutlinedButton(onClick = {
                                val path = running?.outputFile ?: state.outputDir
                                if (path.isNotBlank()) sharePath(context, path)
                            }) { Text("Share output") }
                        }
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Status / logs", style = MaterialTheme.typography.titleMedium)
                        if (state.busy.isNotBlank()) Text("Busy: ${state.busy}")
                        if (state.message.isNotBlank()) Text(state.message)
                        OutlinedButton(onClick = vm::clearLogs) { Text("Clear logs") }
                    }
                }
            }
            items(state.logs.takeLast(200).asReversed()) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private fun sharePath(context: android.content.Context, path: String) {
    val file = File(path)
    val text = if (file.isFile) file.absolutePath else path
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, "Share capture path").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
