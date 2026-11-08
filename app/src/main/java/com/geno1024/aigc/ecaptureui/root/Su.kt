package com.geno1024.aigc.ecaptureui.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class CommandResult(val code: Int, val stdout: String, val stderr: String) {
    val ok: Boolean get() = code == 0
}

object Su {
    suspend fun exec(command: String): CommandResult = withContext(Dispatchers.IO) {
        runCatching {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(false)
                .start()
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            CommandResult(process.waitFor(), stdout.trimEnd(), stderr.trimEnd())
        }.getOrElse { e ->
            CommandResult(127, "", e.message ?: "su failed")
        }
    }

    suspend fun available(): Boolean = exec("id").stdout.contains("uid=0")

    suspend fun installExecutable(src: File, dst: File): CommandResult {
        val qSrc = src.absolutePath.replace("'", "'\\''")
        val qDst = dst.absolutePath.replace("'", "'\\''")
        return exec("cp '$qSrc' '$qDst' && chmod 755 '$qDst'")
    }

    suspend fun killProcessTree(pid: Int): CommandResult = exec("kill -TERM $pid; sleep 1; kill -KILL $pid 2>/dev/null || true")
}
