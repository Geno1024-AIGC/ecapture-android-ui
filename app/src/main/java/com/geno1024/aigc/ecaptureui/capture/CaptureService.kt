package com.geno1024.aigc.ecaptureui.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.geno1024.aigc.ecaptureui.MainActivity
import com.geno1024.aigc.ecaptureui.R
import com.geno1024.aigc.ecaptureui.root.Su
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CaptureService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var process: Process? = null
    private var readerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCapture(
                binary = intent.getStringExtra(EXTRA_BINARY).orEmpty(),
                args = intent.getStringExtra(EXTRA_ARGS).orEmpty(),
                outputDir = intent.getStringExtra(EXTRA_OUTPUT_DIR).orEmpty(),
            )
            ACTION_STOP -> scope.launch { stopCapture() }
        }
        return START_NOT_STICKY
    }

    private fun startCapture(binary: String, args: String, outputDir: String) {
        if (process != null) return
        ensureChannel()
        val outFile = File(outputDir.ifBlank { filesDir.absolutePath }, "capture-${ts()}.log")
        outFile.parentFile?.mkdirs()
        startForeground(NOTIF_ID, notification("Starting ecapture"))
        scope.launch {
            runCatching {
                val escapedArgs = args.trim()
                val cmd = "cd '${outFile.parentFile!!.absolutePath.replace("'", "'\\''")}' && " +
                    "'${binary.replace("'", "'\\''")}' $escapedArgs >> '${outFile.absolutePath.replace("'", "'\\''")}' 2>&1"
                val proc = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
                process = proc
                val pid = pidOf(proc)
                _state.value = CaptureState.Running(pid, System.currentTimeMillis(), outFile.absolutePath)
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIF_ID, notification("Capturing (pid $pid)"))
                _events.emit("started pid=$pid out=${outFile.absolutePath}")
                readerJob = launch {
                    proc.inputStream.bufferedReader().forEachLine { _events.tryEmit(it) }
                }
                val code = proc.waitFor()
                _events.emit("exit code=$code")
                _state.value = CaptureState.Idle
            }.onFailure {
                _state.value = CaptureState.Error(it.message ?: "capture failed")
                _events.tryEmit("error: ${it.message}")
            }
            cleanup()
        }
    }

    private suspend fun stopCapture() {
        val pid = (state.value as? CaptureState.Running)?.pid
        process?.destroy()
        if (pid != null && pid > 0) Su.killProcessTree(pid)
        cleanup()
        _state.value = CaptureState.Idle
        _events.emit("stopped")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanup() {
        readerJob?.cancel()
        process = null
        readerJob = null
    }

    private fun notification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(pi)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(pi)
                .setOngoing(true)
                .build()
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "eCapture", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun pidOf(process: Process): Int = runCatching {
        val f = process.javaClass.getDeclaredField("pid").apply { isAccessible = true }
        f.getInt(process)
    }.getOrDefault(-1)

    private fun ts(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "ecapture"
        private const val NOTIF_ID = 1001
        const val ACTION_START = "com.geno1024.aigc.ecaptureui.START"
        const val ACTION_STOP = "com.geno1024.aigc.ecaptureui.STOP"
        const val EXTRA_BINARY = "binary"
        const val EXTRA_ARGS = "args"
        const val EXTRA_OUTPUT_DIR = "output_dir"

        private val _state = MutableStateFlow<CaptureState>(CaptureState.Idle)
        val state = _state.asStateFlow()
        private val _events = MutableSharedFlow<String>(extraBufferCapacity = 256)
        val events = _events.asSharedFlow()

        fun start(context: Context, binary: String, args: String, outputDir: String) {
            val i = Intent(context, CaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_BINARY, binary)
                putExtra(EXTRA_ARGS, args)
                putExtra(EXTRA_OUTPUT_DIR, outputDir)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i) else context.startService(i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, CaptureService::class.java).apply { action = ACTION_STOP })
        }
    }
}
