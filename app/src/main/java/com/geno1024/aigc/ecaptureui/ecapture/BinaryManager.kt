package com.geno1024.aigc.ecaptureui.ecapture

import android.content.Context
import android.os.Build
import com.geno1024.aigc.ecaptureui.root.Su
import java.io.File

class BinaryManager(private val context: Context) {
    fun defaultInstallDir(): File = File(context.filesDir, "bin")
    fun installedBinary(): File = File(defaultInstallDir(), "ecapture")

    fun deviceAbi(): String = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()

    suspend fun installFromFile(source: File): Result<File> = runCatching {
        require(source.exists()) { "Source file does not exist: ${source.absolutePath}" }
        val dst = installedBinary()
        dst.parentFile?.mkdirs()
        val res = Su.installExecutable(source, dst)
        check(res.ok) { "Install failed: ${res.stderr.ifBlank { res.stdout }}" }
        dst
    }

    suspend fun check(binary: File): Result<String> = runCatching {
        val q = binary.absolutePath.replace("'", "'\\''")
        val res = Su.exec("test -x '$q' && '$q' --help 2>&1 | head -n 5")
        if (res.ok) res.stdout.ifBlank { "ok" } else error(res.stderr.ifBlank { res.stdout }.ifBlank { "not executable" })
    }
}
